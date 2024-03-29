/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.treeConflict;

import consulo.localHistory.LocalHistory;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusConsumer;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 5/2/12
* Time: 1:03 PM
*/
public class SvnTreeConflictResolver {

  @Nonnull
  private final SvnVcs myVcs;
  @Nonnull
  private final FilePath myPath;
  @Nullable
  private final FilePath myRevertPath;
  @Nonnull
  private final VcsDirtyScopeManager myDirtyScopeManager;

  public SvnTreeConflictResolver(@Nonnull SvnVcs vcs, @Nonnull FilePath path, @Nullable FilePath revertPath) {
    myVcs = vcs;
    myPath = path;
    myRevertPath = revertPath;
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());
  }

  public void resolveSelectTheirsFull() throws VcsException
  {
    final LocalHistory localHistory = LocalHistory.getInstance();
    String pathPresentation = TreeConflictRefreshablePanel.filePath(myPath);

    localHistory.putSystemLabel(myVcs.getProject(), "Before accepting theirs for " + pathPresentation);
    try {
      updateToTheirsFull();
      pathDirty(myPath);
      revertAdditional();
    } finally {
      localHistory.putSystemLabel(myVcs.getProject(), "After accepting theirs for " + pathPresentation);
    }
  }

  private void pathDirty(@Nonnull FilePath path) {
    VirtualFile validParent = ChangesUtil.findValidParentAccurately(path);

    if (validParent != null) {
      validParent.refresh(false, true);

      if (path.isDirectory()) {
        myDirtyScopeManager.dirDirtyRecursively(path);
      }
      else {
        myDirtyScopeManager.fileDirty(path);
      }
    }
  }

  private void revertAdditional() throws VcsException
  {
    if (myRevertPath != null) {
      final File ioFile = myRevertPath.getIOFile();
      final Status status = myVcs.getFactory(ioFile).createStatusClient().doStatus(ioFile, false);

      revert(ioFile);
      if (StatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
        FileUtil.delete(ioFile);
      }
      pathDirty(myRevertPath);
    }
  }

  public void resolveSelectMineFull() throws VcsException
  {
    final File ioFile = myPath.getIOFile();

    myVcs.getFactory(ioFile).createConflictClient().resolve(ioFile, Depth.INFINITY, true, true, true);
    pathDirty(myPath);
  }

  private void updateToTheirsFull() throws VcsException
  {
    final File ioFile = myPath.getIOFile();
    Status status = myVcs.getFactory(ioFile).createStatusClient().doStatus(ioFile, false);

    if (status == null || StatusType.STATUS_UNVERSIONED.equals(status.getNodeStatus())) {
      revert(ioFile);
      updateFile(ioFile, SVNRevision.HEAD);
    } else if (StatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
      revert(ioFile);
      updateFile(ioFile, SVNRevision.HEAD);
      FileUtil.delete(ioFile);
    } else {
      Set<File> usedToBeAdded = myPath.isDirectory() ? getDescendantsWithAddedStatus(ioFile) : new HashSet<>();

      revert(ioFile);
      for (File wasAdded : usedToBeAdded) {
        FileUtil.delete(wasAdded);
      }
      updateFile(ioFile, SVNRevision.HEAD);
    }
  }

  @Nonnull
  private Set<File> getDescendantsWithAddedStatus(@Nonnull File ioFile) throws SvnBindException {
    final Set<File> result = new HashSet<>();
    StatusClient statusClient = myVcs.getFactory(ioFile).createStatusClient();

    statusClient.doStatus(ioFile, SVNRevision.UNDEFINED, Depth.INFINITY, false, false, false, false,
                          new StatusConsumer() {
                            @Override
                            public void consume(Status status) throws SVNException {
                              if (status != null && StatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
                                result.add(status.getFile());
                              }
                            }
                          }, null);

    return result;
  }

  private void revert(@Nonnull File file) throws VcsException
  {
    myVcs.getFactory(file).createRevertClient().revert(Collections.singletonList(file), Depth.INFINITY, null);
  }

  private void updateFile(@Nonnull File file, @Nonnull SVNRevision revision) throws SvnBindException {
    boolean useParentAsTarget = !file.exists();
    File target = useParentAsTarget ? file.getParentFile() : file;

    myVcs.getFactory(target).createUpdateClient().doUpdate(target, revision, Depth.INFINITY, useParentAsTarget, false);
  }
}
