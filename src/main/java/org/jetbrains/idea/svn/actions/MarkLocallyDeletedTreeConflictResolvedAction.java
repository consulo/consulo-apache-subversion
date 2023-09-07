/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangesListView;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.ref.Ref;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.LocallyDeletedChange;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnLocallyDeletedChange;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 *         Date: 6/8/11
 *         Time: 4:58 PM
 */
public class MarkLocallyDeletedTreeConflictResolvedAction extends AnAction {
  public MarkLocallyDeletedTreeConflictResolvedAction() {
    super(SvnBundle.message("action.mark.tree.conflict.resolved.text"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final MyLocallyDeletedChecker locallyDeletedChecker = new MyLocallyDeletedChecker(e);
    if (! locallyDeletedChecker.isEnabled()) return;

    final String markText = SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.title");
    final Project project = locallyDeletedChecker.getProject();
    final int result = Messages.showYesNoDialog(project,
                                                SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.text"), markText,
                                                Messages.getQuestionIcon());
    if (result == Messages.YES) {
      final Ref<VcsException> exception = new Ref<>();
      ProgressManager.getInstance().run(new Task.Backgroundable(project, markText, true) {
        public void run(@Nonnull ProgressIndicator indicator) {
          resolveLocallyDeletedTextConflict(locallyDeletedChecker, exception);
        }
      });
      if (! exception.isNull()) {
        AbstractVcsHelper.getInstance(project).showError(exception.get(), markText);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final MyLocallyDeletedChecker locallyDeletedChecker = new MyLocallyDeletedChecker(e);
    e.getPresentation().setVisible(locallyDeletedChecker.isEnabled());
    e.getPresentation().setEnabled(locallyDeletedChecker.isEnabled());
    //e.getPresentation().setText(SvnBundle.message("action.mark.tree.conflict.resolved.text"));
  }

  private void resolveLocallyDeletedTextConflict(MyLocallyDeletedChecker checker, Ref<VcsException> exception) {
    final FilePath path = checker.getPath();
    resolve(checker.getProject(), exception, path);
    VcsDirtyScopeManager.getInstance(checker.getProject()).filePathsDirty(Collections.singletonList(path), null);
  }

  private void resolve(Project project, Ref<VcsException> exception, FilePath path) {
    SvnVcs vcs = SvnVcs.getInstance(project);

    try {
      vcs.getFactory(path.getIOFile()).createConflictClient().resolve(path.getIOFile(), Depth.EMPTY, false, false, true);
    }
    catch (VcsException e) {
      exception.set(e);
    }
  }

  private static class MyLocallyDeletedChecker {
    private final boolean myEnabled;
    private final FilePath myPath;
    private final Project myProject;

    public MyLocallyDeletedChecker(final AnActionEvent e) {
      myProject = e.getData(Project.KEY);
      if (myProject == null) {
        myPath = null;
        myEnabled = false;
        return;
      }

      final List<LocallyDeletedChange> missingFiles = e.getData(ChangesListView.LOCALLY_DELETED_CHANGES);

      if (missingFiles == null || missingFiles.isEmpty()) {
        myPath = null;
        myEnabled = false;
        return;
      }
      /*if (missingFiles == null || missingFiles.size() != 1) {
        final Change[] changes = e.getData(VcsDataKeys.CHANGES);
        if (changes == null || changes.length != 1 || changes[0].getAfterRevision() != null) {
          myPath = null;
          myEnabled = false;
          return;
        }
        myEnabled = changes[0] instanceof ConflictedSvnChange && ((ConflictedSvnChange) changes[0]).getConflictState().isTree();
        if (myEnabled) {
          myPath = changes[0].getBeforeRevision().getFile();
        } else {
          myPath = null;
        }
        return;
      } */

      final LocallyDeletedChange change = missingFiles.get(0);
      myEnabled = change instanceof SvnLocallyDeletedChange && ((SvnLocallyDeletedChange) change).getConflictState().isTree();
      if (myEnabled) {
        myPath = change.getPath();
      }
      else {
        myPath = null;
      }
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public FilePath getPath() {
      return myPath;
    }

    public Project getProject() {
      return myProject;
    }
  }
}
