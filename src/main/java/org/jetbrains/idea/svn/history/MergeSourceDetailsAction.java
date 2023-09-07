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
package org.jetbrains.idea.svn.history;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnIcons;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class MergeSourceDetailsAction extends AnAction implements DumbAware {

  public MergeSourceDetailsAction() {
    super("Show merge sources details", null, SvnIcons.MergeSourcesDetails);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(enabled(e));
  }

  public void registerSelf(final JComponent comp) {
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.ALT_MASK | KeyEvent.CTRL_MASK)), comp);
  }

  private boolean enabled(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (revisionVirtualFile == null) return false;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision == null) return false;
    if (! (revision instanceof SvnFileRevision)) return false;
    return ! ((SvnFileRevision) revision).getMergeSources().isEmpty();
  }

  public void actionPerformed(AnActionEvent e) {
    if (! enabled(e)) return;

    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    SvnMergeSourceDetails.showMe(project, (SvnFileRevision) revision, revisionVirtualFile);
  }
}
