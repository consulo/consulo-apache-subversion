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

import consulo.disposer.Disposable;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ContextHelpAction;
import consulo.language.editor.PlatformDataKeys;
import consulo.application.dumb.DumbAware;
import consulo.ide.impl.idea.openapi.util.Disposer;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;

import javax.swing.*;
import java.awt.*;

public class BrowseRepositoryAction extends AnAction implements DumbAware {
  public static final String REPOSITORY_BROWSER_TOOLWINDOW = "SVN Repositories";

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      RepositoryBrowserDialog dialog = new RepositoryBrowserDialog(ProjectManager.getInstance().getDefaultProject());
      dialog.show();
    }
    else {
      ToolWindowManager manager = ToolWindowManager.getInstance(project);
      ToolWindow w = manager.getToolWindow(REPOSITORY_BROWSER_TOOLWINDOW);
      if (w == null) {
        RepositoryToolWindowPanel component = new RepositoryToolWindowPanel(project);
        w = manager.registerToolWindow(REPOSITORY_BROWSER_TOOLWINDOW, true, ToolWindowAnchor.BOTTOM, project, true);
        final Content content = ContentFactory.SERVICE.getInstance().createContent(component, "", false);
        Disposer.register(content, component);
        w.getContentManager().addContent(content);
      }
      w.show(null);
      w.activate(null);
    }
  }

  private static class RepositoryToolWindowPanel extends JPanel implements Disposable
  {
    private final RepositoryBrowserDialog myDialog;
    private final Project myProject;

    private RepositoryToolWindowPanel(final Project project) {
      super(new BorderLayout());
      myProject = project;

      myDialog = new RepositoryBrowserDialog(project);
      JComponent component = myDialog.createBrowserComponent(true);

      add(component, BorderLayout.CENTER);
      add(myDialog.createToolbar(false, new ContextHelpAction("reference.svn.repository")), BorderLayout.WEST);
    }

    public void dispose() {
      myDialog.disposeRepositoryBrowser();
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(BrowseRepositoryAction.REPOSITORY_BROWSER_TOOLWINDOW);
    }
  }
}
