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

import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.ref.Ref;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MarkTreeConflictResolvedAction extends AnAction implements DumbAware {
  private static final String myText = SvnBundle.message("action.mark.tree.conflict.resolved.text");

  public MarkTreeConflictResolvedAction() {
    super(myText);
  }

  @Override
  public void update(AnActionEvent e) {
    final MyChecker checker = new MyChecker(e);
    e.getPresentation().setVisible(checker.isEnabled());
    e.getPresentation().setEnabled(checker.isEnabled());
    e.getPresentation().setText(myText);
  }

  private static class MyChecker {
    private final boolean myEnabled;
    private final ConflictedSvnChange myChange;
    private final Project myProject;

    public MyChecker(final AnActionEvent e) {
      myProject = e.getData(Project.KEY);
      final Change[] changes = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);

      if (myProject == null || changes == null || changes.length != 1) {
        myEnabled = false;
        myChange = null;
        return;
      }

      final Change change = changes[0];
      myEnabled = change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).getConflictState().isTree();
      if (myEnabled) {
        myChange = (ConflictedSvnChange)change;
      }
      else {
        myChange = null;
      }
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public ConflictedSvnChange getChange() {
      return myChange;
    }

    public Project getProject() {
      return myProject;
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final MyChecker checker = new MyChecker(e);
    if (!checker.isEnabled()) return;

    final String markText = SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.title");
    final int result = Messages.showYesNoDialog(checker.getProject(),
                                                SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.text"), markText,
                                                Messages.getQuestionIcon());
    if (result == Messages.YES) {
      final Ref<VcsException> exception = new Ref<>();
      ProgressManager.getInstance().run(new Task.Backgroundable(checker.getProject(), markText, true) {
        public void run(@Nonnull ProgressIndicator indicator) {
          final ConflictedSvnChange change = checker.getChange();
          final FilePath path = change.getTreeConflictMarkHolder();
          SvnVcs vcs = SvnVcs.getInstance(checker.getProject());

          try {
            vcs.getFactory(path.getIOFile()).createConflictClient().resolve(path.getIOFile(), Depth.EMPTY, false, false, true);
          }
          catch (VcsException e) {
            exception.set(e);
          }
          VcsDirtyScopeManager.getInstance(checker.getProject()).filePathsDirty(getDistinctFiles(change), null);
        }
      });
      if (!exception.isNull()) {
        AbstractVcsHelper.getInstance(checker.getProject()).showError(exception.get(), markText);
      }
    }
  }

  private Collection<FilePath> getDistinctFiles(final Change change) {
    final List<FilePath> result = new ArrayList<>(2);
    if (change.getBeforeRevision() != null) {
      result.add(change.getBeforeRevision().getFile());
    }
    if (change.getAfterRevision() != null) {
      if (change.getBeforeRevision() == null || change.getBeforeRevision() != null && (change.isMoved() || change.isRenamed())) {
        result.add(change.getAfterRevision().getFile());
      }
    }
    return result;
  }
}
