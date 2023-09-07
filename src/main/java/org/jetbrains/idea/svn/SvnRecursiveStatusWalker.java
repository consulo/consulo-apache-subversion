/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import consulo.application.util.function.Processor;
import consulo.logging.Logger;
import consulo.component.ProcessCanceledException;
import consulo.application.progress.ProgressIndicator;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import consulo.util.lang.ref.Ref;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.versionControlSystem.util.VcsUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.application.ApplicationManager;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.change.ChangeListManager;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusConsumer;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.LinkedList;

public class SvnRecursiveStatusWalker {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnRecursiveStatusWalker");

  @Nonnull
  private final SvnVcs myVcs;
  @Nonnull
  private final Project myProject;
  @Nonnull
  private final ProjectLevelVcsManager myVcsManager;
  @Nonnull
  private final ChangeListManager myChangeListManager;
  @Nullable private final ProgressIndicator myProgress;
  @Nonnull
  private final StatusReceiver myReceiver;
  @Nonnull
  private final LinkedList<MyItem> myQueue;
  @Nonnull
  private final MyHandler myHandler;
  @Nullable private ISVNStatusFileProvider myFileProvider;

  public SvnRecursiveStatusWalker(@Nonnull SvnVcs vcs, @Nonnull StatusReceiver receiver, @Nullable ProgressIndicator progress) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myReceiver = receiver;
    myProgress = progress;
    myQueue = new LinkedList<>();
    myHandler = new MyHandler();
  }

  public void setFileProvider(@Nullable ISVNStatusFileProvider fileProvider) {
    myFileProvider = fileProvider;
  }

  public void go(@Nonnull FilePath rootPath, @Nonnull Depth depth) throws SvnBindException {
    myQueue.add(createItem(rootPath, depth, false));

    while (!myQueue.isEmpty()) {
      checkCanceled();

      MyItem item = myQueue.removeFirst();
      try {
        if (item.getPath().isDirectory()) {
          processDirectory(item);
        }
        else {
          processFile(item);
        }
      }
      catch (SvnBindException e) {
        handleStatusException(item, e);
      }
    }
  }

  private void processDirectory(@Nonnull MyItem item) throws SvnBindException {
    File ioFile = item.getPath().getIOFile();

    myHandler.setCurrentItem(item);
    item.getClient().doStatus(ioFile, SVNRevision.WORKING, item.getDepth(), false, false, true, true, myHandler, null);

    // check if current item was already processed - not to request its status once again
    if (!myHandler.myMetCurrentItem) {
      myHandler.checkIfCopyRootWasReported(myHandler.getCurrentItemStatus());
    }
  }

  private void processFile(@Nonnull MyItem item) throws SvnBindException {
    try {
      myReceiver.process(item.getPath(), item.getClient().doStatus(item.getPath().getIOFile(), false));
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  public void checkCanceled() {
    if (myProgress != null) {
      myProgress.checkCanceled();
    }
  }

  public boolean isIgnoredByVcs(@Nonnull final VirtualFile vFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (myVcs.getProject().isDisposed()) throw new ProcessCanceledException();
        return myVcsManager.isIgnored(vFile);
      }
    });
  }

  public boolean isIgnoredIdeaLevel(@Nonnull VirtualFile vFile) {
    return myChangeListManager.isIgnoredFile(vFile);
  }

  private void handleStatusException(@Nonnull MyItem item, @Nonnull SvnBindException e) throws SvnBindException {
    if (e.contains(SVNErrorCode.WC_NOT_DIRECTORY) || e.contains(SVNErrorCode.WC_NOT_FILE) || e.contains(SVNErrorCode.WC_PATH_NOT_FOUND)) {
      final VirtualFile virtualFile = item.getPath().getVirtualFile();
      if (virtualFile != null && !isIgnoredByVcs(virtualFile)) {
        // self is unversioned
        myReceiver.processUnversioned(virtualFile);

        if (virtualFile.isDirectory()) {
          processRecursively(virtualFile, item.getDepth());
        }
      }
    }
    else {
      throw e;
    }
  }

  private static class MyItem {
    @Nonnull
	private final FilePath myPath;
    @Nonnull
	private final Depth myDepth;
    @Nonnull
	private final StatusClient myStatusClient;
    private final boolean myIsInnerCopyRoot;

    private MyItem(@Nonnull FilePath path, @Nonnull Depth depth, boolean isInnerCopyRoot, @Nonnull StatusClient statusClient) {
      myPath = path;
      myDepth = depth;
      myStatusClient = statusClient;
      myIsInnerCopyRoot = isInnerCopyRoot;
    }

    @Nonnull
    public FilePath getPath() {
      return myPath;
    }

    @Nonnull
    public Depth getDepth() {
      return myDepth;
    }

    @Nonnull
    public StatusClient getClient() {
      return myStatusClient;
    }

    public boolean isIsInnerCopyRoot() {
      return myIsInnerCopyRoot;
    }
  }

  private void processRecursively(@Nonnull VirtualFile vFile, @Nonnull Depth prevDepth) {
    if (Depth.EMPTY.equals(prevDepth)) return;
    if (isIgnoredIdeaLevel(vFile)) {
      myReceiver.processIgnored(vFile);
      return;
    }
    final Depth newDepth = Depth.INFINITY.equals(prevDepth) ? Depth.INFINITY : Depth.EMPTY;

    final File ioFile = new File(vFile.getPath());
    final Processor<File> processor;
    final Processor<File> directoryFilter;
    final Ref<File> lastIgnored = new Ref<>();
    final Processor<File> checkDirProcessor = new Processor<File>() {
      @Override
      public boolean process(File file) {
        FilePath path = VcsUtil.getFilePath(file, true);
        VirtualFile vf = VfsUtil.findFileByIoFile(file, true);
        if (vf != null && isIgnoredIdeaLevel(vf)) {
          lastIgnored.set(file);
          myReceiver.processIgnored(vf);
        }
        else if (file.isDirectory() && new File(file, SVNFileUtil.getAdminDirectoryName()).exists()) {
          myQueue.add(createItem(path, newDepth, true));
        }
        else if (vf != null) {
          myReceiver.processUnversioned(vf);
        }
        return true;
      }
    };
    if (Depth.EMPTY.equals(newDepth)) {
      // just process immediate children - so only root directory itself should satisfy filter
      directoryFilter = new Processor<File>() {
        @Override
        public boolean process(File file) {
          return FileUtil.filesEqual(ioFile, file);
        }
      };
      processor = new Processor<File>() {
        @Override
        public boolean process(File file) {
          // TODO: check if we should still call checkDirProcessor() here - or we really could not check ignore settings but just call
          // TODO: myReceiver.processUnversioned() for all immediate children
          // here we deal only with immediate children - so ignored on IDEA level for children is not important
          return FileUtil.filesEqual(ioFile, file) || checkDirProcessor.process(file);
        }
      };
    } else {
      directoryFilter = new Processor<File>() {
        @Override
        public boolean process(File file) {
          return ! Comparing.equal(lastIgnored, file) && (myQueue.isEmpty() || ! FileUtil.filesEqual(myQueue.getLast().getPath().getIOFile(), file));
        }
      };
      processor = checkDirProcessor;
    }
    FileUtil.processFilesRecursively(ioFile, processor, directoryFilter);
  }

  @Nonnull
  private MyItem createItem(@Nonnull FilePath path, @Nonnull Depth depth, boolean isInnerCopyRoot) {
    StatusClient statusClient = myVcs.getFactory(path.getIOFile()).createStatusClient(myFileProvider, createEventHandler());

    return new MyItem(path, depth, isInnerCopyRoot, statusClient);
  }

  @Nonnull
  public ProgressTracker createEventHandler() {
    return new ProgressTracker() {
      @Override
      public void consume(ProgressEvent event) throws SVNException {
      }

      @Override
      public void checkCancelled() throws SVNCancelException {
        SvnRecursiveStatusWalker.this.checkCanceled();
      }
    };
  }

  private class MyHandler implements StatusConsumer {
    private MyItem myCurrentItem;
    private boolean myMetCurrentItem;

    public void setCurrentItem(@Nonnull MyItem currentItem) {
      myCurrentItem = currentItem;
      myMetCurrentItem = false;
    }

    public void checkIfCopyRootWasReported(@Nullable Status status) {
      if (!myMetCurrentItem && status != null && FileUtil.filesEqual(status.getFile(), myCurrentItem.getPath().getIOFile())) {
        myMetCurrentItem = true;

        processCurrentItem(status);
      }
    }

    @Nullable
    public Status getCurrentItemStatus() {
      Status result = null;

      try {
        result = myCurrentItem.getClient().doStatus(myCurrentItem.getPath().getIOFile(), false);
      }
      catch (SvnBindException e) {
        LOG.info(e);
      }

      return result;
    }

    public void processCurrentItem(@Nonnull Status status) {
      StatusType nodeStatus = status.getNodeStatus();
      FilePath path = myCurrentItem.getPath();
      VirtualFile vf = path.getVirtualFile();

      if (vf != null) {
        if (StatusType.STATUS_IGNORED.equals(nodeStatus)) {
          myReceiver.processIgnored(vf);
        }
        else if (StatusType.STATUS_UNVERSIONED.equals(nodeStatus) || StatusType.UNKNOWN.equals(nodeStatus)) {
          myReceiver.processUnversioned(vf);
          processRecursively(vf, myCurrentItem.getDepth());
        }
        else if (!StatusType.OBSTRUCTED.equals(nodeStatus) && !StatusType.STATUS_NONE.equals(nodeStatus)) {
          if (myCurrentItem.isIsInnerCopyRoot()) {
            myReceiver.processCopyRoot(vf, status.getURL(), myVcs.getWorkingCopyFormat(path.getIOFile()), status.getRepositoryRootURL());
          }
          else {
            myReceiver.bewareRoot(vf, status.getURL());
          }
        }
      }
    }

    @Override
    public void consume(final Status status) throws SVNException {
      checkCanceled();
      final File ioFile = status.getFile();
      checkIfCopyRootWasReported(status);

      VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
      if (vFile != null && isIgnoredByVcs(vFile)) return;
      if (myProject.isDisposed()) throw new ProcessCanceledException();

      if (vFile != null && status.is(StatusType.STATUS_UNVERSIONED)) {
        if (vFile.isDirectory()) {
          if (!FileUtil.filesEqual(myCurrentItem.getPath().getIOFile(), ioFile)) {
            myQueue.add(createItem(VcsUtil.getFilePath(vFile), Depth.INFINITY, true));
          }
        }
        else {
          myReceiver.processUnversioned(vFile);
        }
      }
      else {
        myReceiver.process(VcsUtil.getFilePath(ioFile, status.getKind().isDirectory()), status);
      }
    }
  }
}
