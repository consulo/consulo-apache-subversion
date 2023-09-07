/*
 * Copyright 2000-2014 JetBrains s.r.o.
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


package org.jetbrains.idea.svn.actions;

import consulo.dataContext.DataContext;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.VcsException;
import consulo.util.collection.ArrayUtil;
import javax.annotation.Nonnull;

import consulo.project.Project;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.status.FileStatusManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.conflict.ConflictClient;
import org.jetbrains.idea.svn.dialogs.SelectFilesDialog;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusConsumer;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.Collection;
import java.util.TreeSet;

public class MarkResolvedAction extends BasicAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.actions.MarkResolvedAction");

  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.name.mark.resolved");
  }

  protected boolean needsAllFiles() {
    return false;
  }

  protected boolean isEnabled(Project project, @Nonnull SvnVcs vcs, VirtualFile file) {
    if (file.isDirectory()) {
      return SvnStatusUtil.isUnderControl(project, file);
    }
    final FileStatus fStatus = FileStatusManager.getInstance(project).getStatus(file);
    return FileStatus.MERGED_WITH_CONFLICTS.equals(fStatus) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(fStatus) ||
           FileStatus.MERGED_WITH_PROPERTY_CONFLICTS.equals(fStatus);
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context);
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] files, DataContext context)
    throws VcsException {
    SvnVcs vcs = SvnVcs.getInstance(project);
    ApplicationManager.getApplication().saveAll();
    Collection<String> paths = collectResolvablePaths(vcs, files);
    if (paths.isEmpty()) {
      Messages.showInfoMessage(project, SvnBundle.message("message.text.no.conflicts.found"),
                               SvnBundle.message("message.title.no.conflicts.found"));
      return;
    }
    String[] pathsArray = ArrayUtil.toStringArray(paths);
    SelectFilesDialog dialog = new SelectFilesDialog(project, SvnBundle.message("label.select.files.and.directories.to.mark.resolved"),
                                                     SvnBundle.message("dialog.title.mark.resolved"),
                                                     SvnBundle.message("action.name.mark.resolved"), pathsArray, "vcs.subversion.resolve"
    );
    if (!dialog.showAndGet()) {
      return;
    }
    pathsArray = dialog.getSelectedPaths();
    try {
      for (String path : pathsArray) {
        File ioFile = new File(path);
        ConflictClient client = vcs.getFactory(ioFile).createConflictClient();

        // TODO: Probably false should be passed to "resolveTree", but previous logic used true implicitly
        client.resolve(ioFile, Depth.EMPTY, true, true, true);
      }
    }
    finally {
      for (VirtualFile file : files) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(file);
        file.refresh(true, false);
        if (file.getParent() != null) {
          file.getParent().refresh(true, false);
        }
      }
    }
  }

  protected boolean isBatchAction() {
    return true;
  }

  private static Collection<String> collectResolvablePaths(final SvnVcs vcs, VirtualFile[] files) {
    final Collection<String> target = new TreeSet<>();
    for (VirtualFile file : files) {
      try {
        File path = new File(file.getPath());
        StatusClient client = vcs.getFactory(path).createStatusClient();

        client.doStatus(path, SVNRevision.UNDEFINED, Depth.INFINITY, false, false, false, false, new StatusConsumer() {
          @Override
          public void consume(Status status) {
            if (status.getContentsStatus() == StatusType.STATUS_CONFLICTED ||
                status.getPropertiesStatus() == StatusType.STATUS_CONFLICTED) {
              target.add(status.getFile().getAbsolutePath());
            }
          }
        }, null);
      }
      catch (SvnBindException e) {
        LOG.warn(e);
      }
    }
    return target;
  }
}
