/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.NotNullFunction;
import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.dialogs.CopiesPanel;

import javax.swing.*;

public class WorkingCopiesContent implements ChangesViewContentProvider {

  public static final String TAB_NAME = SvnBundle.message("dialog.show.svn.map.title");

  @Nonnull
  private final Project myProject;

  public WorkingCopiesContent(@Nonnull Project project) {
    myProject = project;
  }

  @Override
  public JComponent initContent() {
    return new CopiesPanel(myProject).getComponent();
  }

  @Override
  public void disposeContent() {
  }

  public static void show(@Nonnull Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(project);
    if (manager != null) {
      final ToolWindow window = manager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
      if (window != null) {
        window.show(null);
        final ContentManager cm = window.getContentManager();
        final Content content = cm.findContent(TAB_NAME);
        if (content != null) {
          cm.setSelectedContent(content, true);
        }
      }
    }
  }

  public static class VisibilityPredicate implements NotNullFunction<Project, Boolean> {

    @Nonnull
    @Override
    public Boolean fun(@Nonnull Project project) {
      return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
    }
  }
}
