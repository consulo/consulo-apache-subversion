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
package org.jetbrains.idea.svn.checkin;

import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.util.FunctionUtil;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.util.collection.MultiMap;
import consulo.util.collection.Sets;
import consulo.util.lang.Pair;
import consulo.util.lang.function.PairConsumer;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.FilePathComparator;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.checkin.CheckinEnvironment;
import consulo.versionControlSystem.checkin.CheckinProjectPanel;
import consulo.versionControlSystem.ui.RefreshableOnComponent;
import consulo.versionControlSystem.ui.VcsBalloonProblemNotifier;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.function.Function;

public class SvnCheckinEnvironment implements CheckinEnvironment {

  private static final Logger LOG = Logger.getInstance(SvnCheckinEnvironment.class);
  @Nonnull
  private final SvnVcs mySvnVcs;

  public SvnCheckinEnvironment(@Nonnull SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return new KeepLocksComponent();
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }

  private void doCommit(@Nonnull Collection<FilePath> committables,
                        String comment,
                        List<VcsException> exception,
                        final Set<String> feedback) {
    //noinspection unchecked
    MultiMap<Pair<SVNURL, WorkingCopyFormat>, FilePath> map = SvnUtil.splitIntoRepositoriesMap(mySvnVcs, committables, Convertor.SELF);

    for (Map.Entry<Pair<SVNURL, WorkingCopyFormat>, Collection<FilePath>> entry : map.entrySet()) {
      try {
        doCommitOneRepo(entry.getValue(), comment, exception, feedback, entry.getKey().getSecond());
      }
      catch (VcsException e) {
        LOG.info(e);
        exception.add(e);
      }
    }
  }

  private void doCommitOneRepo(@Nonnull Collection<FilePath> committables,
                               String comment,
                               List<VcsException> exception,
                               final Set<String> feedback,
                               @Nonnull WorkingCopyFormat format)
    throws VcsException {
    if (committables.isEmpty()) {
      return;
    }

    CommitInfo[] results = mySvnVcs.getFactory(format).createCheckinClient().commit(ChangesUtil.filePathsToFiles(committables), comment);

    final StringBuilder committedRevisions = new StringBuilder();
    for (CommitInfo result : results) {
      if (result.getErrorMessage() != null) {
        exception.add(new VcsException(result.getErrorMessage().getFullMessage()));
      }
      else if (result != CommitInfo.EMPTY && result.getRevision() > 0) {
        if (committedRevisions.length() > 0) {
          committedRevisions.append(", ");
        }
        committedRevisions.append(result.getRevision());
      }
    }
    if (committedRevisions.length() > 0) {
      reportCommittedRevisions(feedback, committedRevisions.toString());
    }
  }

  private void reportCommittedRevisions(Set<String> feedback, String committedRevisions) {
    final Project project = mySvnVcs.getProject();
    final String message = SvnBundle.message("status.text.comitted.revision", committedRevisions);
    if (feedback == null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          new VcsBalloonProblemNotifier(project, message, NotificationType.INFORMATION).run();
        }
      }, () -> (!project.isOpen()) || project.isDisposed());
    }
    else {
      feedback.add("Subversion: " + message);
    }
  }

  @Nonnull
  private Collection<FilePath> getCommitables(@Nonnull List<Change> changes) {
    Set<FilePath> result = Sets.newHashSet(ChangesUtil.FILE_PATH_BY_PATH_ONLY_HASHING_STRATEGY);

    ChangesUtil.getAllPaths(changes.stream()).forEach(path -> {
      if (result.add(path)) {
        addParents(result, path);
      }
    });

    return result;
  }

  private void addParents(@Nonnull Collection<FilePath> paths, @Nonnull FilePath path) {
    FilePath parent = path;

    while ((parent = parent.getParentPath()) != null && isAddedOrReplaced(parent)) {
      paths.add(parent);
    }
  }

  private boolean isAddedOrReplaced(@Nonnull FilePath file) {
    Status status = getStatus(file);

    return status != null && status.is(StatusType.STATUS_ADDED, StatusType.STATUS_REPLACED);
  }

  @Nullable
  private Status getStatus(@Nonnull FilePath file) {
    Status result = null;

    try {
      result = mySvnVcs.getFactory(file.getIOFile()).createStatusClient().doStatus(file.getIOFile(), false);
    }
    catch (SvnBindException e) {
      LOG.info(e);
    }

    return result;
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  public List<VcsException> commit(List<Change> changes,
                                   final String preparedComment,
                                   @Nonnull Function<Object, Object> parametersHolder,
                                   final Set<String> feedback) {
    final List<VcsException> exception = new ArrayList<>();
    final Collection<FilePath> committables = getCommitables(changes);
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    if (progress != null) {
      doCommit(committables, preparedComment, exception, feedback);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          doCommit(committables, preparedComment, exception, feedback);
        }
      }, SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject());
    }
    else {
      doCommit(committables, preparedComment, exception, feedback);
    }

    return exception;
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.nullConstant(), null);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> filePaths) {
    List<VcsException> exceptions = new ArrayList<>();
    List<File> files = ChangesUtil.filePathsToFiles(filePaths);

    for (File file : files) {
      try {
        mySvnVcs.getFactory(file).createDeleteClient().delete(file, true, false, null);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(mySvnVcs, files);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@Nonnull SvnVcs vcs, List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(vcs, files, false);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@Nonnull SvnVcs vcs,
                                                                       List<VirtualFile> files,
                                                                       final boolean recursive) {
    Collections.sort(files, FilePathComparator.getInstance());

    ProgressTracker eventHandler = new SvnProgressCanceller() {
      @Override
      public void consume(ProgressEvent event) throws SVNException {
        // TODO: indicator is null here when invoking "Add" action
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        File file = event.getFile();

        if (indicator != null && file != null) {
          indicator.setText(SvnBundle.message("progress.text2.adding", file.getName() + " (" + file.getParent() + ")"));
        }
      }
    };

    List<VcsException> exceptions = new ArrayList<>();
    Depth depth = Depth.allOrEmpty(recursive);

    for (VirtualFile file : files) {
      try {
        File convertedFile = VfsUtilCore.virtualToIoFile(file);

        vcs.getFactory(convertedFile).createAddClient().add(convertedFile, depth, true, false, true, eventHandler);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  private class KeepLocksComponent implements RefreshableOnComponent {

    @Nonnull
    private final JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    @Nonnull
    private final JPanel myPanel;
    @Nonnull
    private final JCheckBox myAutoUpdate;

    public KeepLocksComponent() {

      myPanel = new JPanel(new BorderLayout());
      myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
      myKeepLocksBox.setSelected(myIsKeepLocks);
      myAutoUpdate = new JCheckBox("Auto-update after commit");

      myPanel.add(myAutoUpdate, BorderLayout.NORTH);
      myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isKeepLocks() {
      return myKeepLocksBox.isSelected();
    }

    public boolean isAutoUpdate() {
      return myAutoUpdate.isSelected();
    }

    public void refresh() {
    }

    public void saveState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      configuration.setKeepLocks(isKeepLocks());
      configuration.setAutoUpdateAfterCommit(isAutoUpdate());
    }

    public void restoreState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      myIsKeepLocks = configuration.isKeepLocks();
      myAutoUpdate.setSelected(configuration.isAutoUpdateAfterCommit());
    }
  }
}
