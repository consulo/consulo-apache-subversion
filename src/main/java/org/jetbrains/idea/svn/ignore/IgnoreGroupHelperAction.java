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
package org.jetbrains.idea.svn.ignore;

import consulo.dataContext.DataContext;
import consulo.ui.ex.action.AnActionEvent;
import consulo.project.Project;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.BasicAction;

public class IgnoreGroupHelperAction extends BasicAction {
  private boolean myAllCanBeIgnored;
  private boolean myAllAreIgnored;
  private FileIterationListener myListener;

  protected String getActionName(final AbstractVcs vcs) {
    return null;
  }

  public void update(final AnActionEvent e) {
    myAllAreIgnored = true;
    myAllCanBeIgnored = true;

    super.update(e);
  }

  public void setFileIterationListener(final FileIterationListener listener) {
    myListener = listener;
  }

  private boolean isEnabledImpl(final SvnVcs vcs, final VirtualFile file) {
    final ChangeListManager clManager = ChangeListManager.getInstance(vcs.getProject());

    if (SvnStatusUtil.isIgnoredInAnySense(clManager, file)) {
      myAllCanBeIgnored = false;
      return myAllAreIgnored | myAllCanBeIgnored;
    } else if (clManager.isUnversioned(file)) {
      // check parent
      final VirtualFile parent = file.getParent();
      if (parent != null) {
        if ((! SvnStatusUtil.isIgnoredInAnySense(clManager, parent)) && (! clManager.isUnversioned(parent))) {
          myAllAreIgnored = false;
          return myAllAreIgnored | myAllCanBeIgnored;
        }
      }
    }
    myAllCanBeIgnored = false;
    myAllAreIgnored = false;
    return false;
  }

  protected boolean isEnabled(final Project project, final SvnVcs vcs, final VirtualFile file) {
    final boolean result = isEnabledImpl(vcs, file);
    if (result) {
      myListener.onFileEnabled(file);
    }
    return result;
  }

  public boolean allCanBeIgnored() {
    return myAllCanBeIgnored;
  }

  public boolean allAreIgnored() {
    return myAllAreIgnored;
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(final Project project, final SvnVcs activeVcs, final VirtualFile file, final DataContext context)
      throws VcsException
  {

  }

  protected void batchPerform(final Project project, final SvnVcs activeVcs, final VirtualFile[] file, final DataContext context)
      throws VcsException
  {

  }

  protected boolean isBatchAction() {
    return false;
  }
}
