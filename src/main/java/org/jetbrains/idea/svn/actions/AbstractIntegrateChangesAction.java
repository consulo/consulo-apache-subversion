/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import consulo.ide.impl.idea.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesActionPerformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class AbstractIntegrateChangesAction<T extends SelectedCommittedStuffChecker> extends AnAction implements DumbAware {
  private final boolean myCheckUseCase;

  protected AbstractIntegrateChangesAction(final boolean checkUseCase) {
    myCheckUseCase = checkUseCase;
  }

  @Nonnull
  protected abstract MergerFactory createMergerFactory(final T checker);

  @Nonnull
  protected abstract T createChecker();

  public final void update(final AnActionEvent e) {
    final Project project = e.getData(Project.KEY);
    final CommittedChangesBrowserUseCase useCase = e.getData(CommittedChangesBrowserUseCase.DATA_KEY);
    final Presentation presentation = e.getPresentation();

    if ((project == null) || (myCheckUseCase) && ((useCase == null) || (!CommittedChangesBrowserUseCase.COMMITTED.equals(useCase)))) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setText(SvnBundle.message("action.Subversion.integrate.changes.actionname"));
    presentation.setDescription(SvnBundle.message("action.Subversion.integrate.changes.description"));

    final T checker = createChecker();
    checker.execute(e);

    presentation.setVisible(true);
    presentation.setEnabled(checker.isValid());

    if (presentation.isVisible() && presentation.isEnabled() &&
      ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      presentation.setEnabled(false);
    }

    updateWithChecker(e, checker);
  }

  protected void updateWithChecker(final AnActionEvent e, SelectedCommittedStuffChecker checker) {
  }

  @Nullable
  protected abstract String getSelectedBranchUrl(SelectedCommittedStuffChecker checker);

  @Nullable
  protected abstract String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker);

  @Nullable
  protected abstract String getDialogTitle();

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(Project.KEY);

    final T checker = createChecker();
    checker.execute(e);

    if (!checker.isValid()) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.no.available.files.text"),
                               SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
      return;
    }

    final SvnIntegrateChangesActionPerformer changesActionPerformer =
      new SvnIntegrateChangesActionPerformer(project, checker.getSameBranch(), createMergerFactory(checker));

    final String selectedBranchUrl = getSelectedBranchUrl(checker);
    if (selectedBranchUrl == null) {
      SelectBranchPopup.showForBranchRoot(project, checker.getRoot(), changesActionPerformer,
                                          SvnBundle.message("action.Subversion.integrate.changes.select.branch.text"));
    }
    else {
      changesActionPerformer.onBranchSelected(selectedBranchUrl, getSelectedBranchLocalPath(checker), getDialogTitle());
    }
  }
}
