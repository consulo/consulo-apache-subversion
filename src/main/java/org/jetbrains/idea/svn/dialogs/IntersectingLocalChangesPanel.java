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
package org.jetbrains.idea.svn.dialogs;

import consulo.dataContext.DataManager;
import consulo.ide.impl.idea.ide.actions.EditSourceAction;
import consulo.ide.impl.idea.openapi.util.BooleanGetter;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangesBrowserNode;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.TreeModelBuilder;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.ui.ex.awt.BorderLayoutPanel;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.internal.laf.MultiLineLabelUI;
import consulo.ui.ex.awt.tree.SimpleTree;
import consulo.ui.ex.awt.tree.TreeUIHelper;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.content.ContentsUtil;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.versionControlSystem.FilePath;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Objects;

import static consulo.ide.impl.idea.util.containers.UtilKt.stream;
import static consulo.versionControlSystem.change.ChangesUtil.getNavigatableArray;

public class IntersectingLocalChangesPanel {

  @Nonnull
  private final BorderLayoutPanel myPanel;
  @Nonnull
  private final List<FilePath> myFiles;
  @Nonnull
  private final Project myProject;

  public IntersectingLocalChangesPanel(@Nonnull Project project, @Nonnull List<FilePath> files, @Nonnull String text) {
    myProject = project;
    myFiles = files;
    myPanel = createPanel(createLabel(text), createTree());
  }

  @Nonnull
  private BorderLayoutPanel createPanel(@Nonnull JLabel label, @Nonnull JTree tree) {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel();

    panel.setBackground(UIUtil.getTextFieldBackground());
    panel.addToTop(label).addToCenter(tree);
    new EditSourceAction().registerCustomShortcutSet(CommonShortcuts.getEditSource(), panel);

    DataManager.registerDataProvider(panel, dataId -> {
      if (CommonDataKeys.NAVIGATABLE_ARRAY == dataId) {
        return getNavigatableArray(myProject, stream(tree.getSelectionPaths())
          .map(TreePath::getLastPathComponent)
          .map(node -> (ChangesBrowserNode<?>)node)
          .flatMap(ChangesBrowserNode::getFilePathsUnderStream)
          .map(FilePath::getVirtualFile)
          .filter(Objects::nonNull)
          .distinct());
      }

      return null;
    });

    return panel;
  }

  @Nonnull
  private SimpleTree createTree() {
    SimpleTree tree = new SimpleTree(TreeModelBuilder.buildFromFilePaths(myProject, true, myFiles)) {
      @Override
      protected void configureUiHelper(@Nonnull TreeUIHelper helper) {
        super.configureUiHelper(helper);
        helper.installEditSourceOnDoubleClick(this);
        helper.installEditSourceOnEnterKeyHandler(this);
      }
    };
    tree.setRootVisible(false);
    tree.setShowsRootHandles(false);
    tree.setCellRenderer(new ChangesBrowserNodeRenderer(myProject, BooleanGetter.TRUE, false));

    return tree;
  }

  @Nonnull
  private static JBLabel createLabel(@Nonnull String text) {
    JBLabel label = new JBLabel(text) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, (int)(size.height * 1.7));
      }
    };
    label.setUI(new MultiLineLabelUI());
    label.setBackground(UIUtil.getTextFieldBackground());
    label.setVerticalTextPosition(SwingConstants.TOP);

    return label;
  }

  @SuppressWarnings("SameParameterValue")
  public static void showInVersionControlToolWindow(@Nonnull Project project,
                                                    @Nonnull String title,
                                                    @Nonnull List<FilePath> files,
                                                    @Nonnull String prompt) {
    IntersectingLocalChangesPanel intersectingPanel = new IntersectingLocalChangesPanel(project, files, prompt);
    Content content = ContentFactory.getInstance().createContent(intersectingPanel.myPanel, title, true);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);

    ContentsUtil.addContent(toolWindow.getContentManager(), content, true);
    toolWindow.activate(null);
  }
}
