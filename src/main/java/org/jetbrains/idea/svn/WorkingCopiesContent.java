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

import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangesViewContentManager;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangesViewContentProvider;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import org.jetbrains.idea.svn.dialogs.CopiesPanel;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.function.Function;

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

  public static class VisibilityPredicate implements Function<Project, Boolean> {

    @Nonnull
    @Override
    public Boolean apply(@Nonnull Project project) {
      return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
    }
  }
}
