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

import consulo.application.Application;
import consulo.configurable.ConfigurationException;
import consulo.ide.impl.idea.openapi.ui.NamedConfigurable;
import consulo.ide.impl.idea.openapi.util.Disposer;
import consulo.ide.impl.idea.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangeListViewerDialog;
import consulo.ide.setting.ui.MasterDetailsComponent;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ModalityState;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.content.ContentsUtil;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SvnMergeSourceDetails extends MasterDetailsComponent {
  private final Project myProject;
  private final SvnFileRevision myRevision;
  private final VirtualFile myFile;
  private final Map<Long, SvnChangeList> myListsMap;

  private SvnMergeSourceDetails(final Project project, final SvnFileRevision revision, final VirtualFile file) {
    myProject = project;
    myRevision = revision;
    myFile = file;
    myListsMap = new HashMap<>();
    initTree();
    fillTree();

    getSplitter().setProportion(0.5f);
  }

  public static void showMe(final Project project, final SvnFileRevision revision, final VirtualFile file) {
    Application application = Application.get();
    ModalityState noneModalityState = application.getNoneModalityState();
    ModalityState currentModalityState = application.getCurrentModalityState();

    if (noneModalityState.equals(currentModalityState)) {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
      final ContentManager contentManager = toolWindow.getContentManager();

      final MyDialog dialog = new MyDialog(project, revision, file);
      // TODO: Temporary memory leak fix - rewrite this part not to create dialog if only createCenterPanel(), but not show() is invoked
      Disposer.register(project, dialog.getDisposable());

      Content content = ContentFactory.getInstance().createContent(dialog.createCenterPanel(),
                                                                   SvnBundle.message("merge.source.details.title",
                                                                                     (file == null) ? revision.getURL() : file.getName(),
                                                                                     revision.getRevisionNumber().asString()),
                                                                   true);
      ContentsUtil.addOrReplaceContent(contentManager, content, true);

      toolWindow.activate(null);
    }
    else {
      new MyDialog(project, revision, file).show();
    }
  }

  protected void processRemovedItems() {

  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  @Nls
  public String getDisplayName() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  private void addRecursively(final SvnFileRevision revision, final MyNode node, final List<TreePath> nodesToExpand) {
    final MyNode current = new MyNode(new MyNamedConfigurable(revision, myFile, myProject, myListsMap));
    node.add(current);
    final TreeNode[] path = ((DefaultTreeModel)myTree.getModel()).getPathToRoot(node);
    nodesToExpand.add(new TreePath(path));
    final List<SvnFileRevision> mergeSources = revision.getMergeSources();
    for (SvnFileRevision source : mergeSources) {
      addRecursively(source, current, nodesToExpand);
    }
  }

  private class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    private final static int ourMaxWidth = 100;
    private final static String ourDots = "(...)";

    public void customizeCellRenderer(final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      final FontMetrics metrics = tree.getFontMetrics(tree.getFont());
      final SvnFileRevision revision;
      if (value instanceof MyRootNode) {
        revision = myRevision;
      }
      else {
        final MyNode myNode = (MyNode)value;
        final MyNamedConfigurable configurable = (MyNamedConfigurable)myNode.getConfigurable();
        revision = configurable.getRevision();
      }

      final String revisonNumber = revision.getRevisionNumber().asString();
      final Pair<String, Boolean> info = CommittedChangeListRenderer.getDescriptionOfChangeList(revision.getCommitMessage());
      String description = info.getFirst();
      int width = metrics.stringWidth(description);
      int dotsWidth = metrics.stringWidth(ourDots);
      boolean descriptionTruncated = info.getSecond();
      if ((descriptionTruncated && (ourMaxWidth - dotsWidth < width)) || (!descriptionTruncated) && (ourMaxWidth < width)) {
        description = CommittedChangeListRenderer.truncateDescription(description, metrics, ourMaxWidth - dotsWidth);
        descriptionTruncated = true;
      }
      if (descriptionTruncated) {
        description += ourDots;
      }

      final String date = CommittedChangeListRenderer.getDateOfChangeList(revision.getRevisionDate());

      final String author = revision.getAuthor();

      append(revisonNumber + " ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(description + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(author, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(", " + date, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private void fillTree() {
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myRoot.removeAllChildren();

    final List<TreePath> nodesToExpand = new ArrayList<>();
    addRecursively(myRevision, myRoot, nodesToExpand);

    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    for (TreePath treePath : nodesToExpand) {
      myTree.expandPath(treePath);
    }
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
  }

  private static class MyNamedConfigurable extends NamedConfigurable<SvnFileRevision> {
    private final SvnFileRevision myRevision;
    private final VirtualFile myFile;
    private final Project myProject;
    private final Map<Long, SvnChangeList> myListsMap;
    private JComponent myPanel;

    private MyNamedConfigurable(final SvnFileRevision revision, final VirtualFile file, final Project project,
                                final Map<Long, SvnChangeList> listsMap) {
      myRevision = revision;
      myFile = file;
      myProject = project;
      myListsMap = listsMap;
    }

    public void setDisplayName(final String name) {
    }

    public SvnFileRevision getEditableObject() {
      return myRevision;
    }

    public String getBannerSlogan() {
      return myRevision.getRevisionNumber().asString();
    }

    private SvnChangeList getList() {
      SvnChangeList list = myListsMap.get(myRevision);
      if (list == null) {
        list = (SvnChangeList)SvnVcs.getInstance(myProject).loadRevisions(myFile, myRevision.getRevisionNumber());
        myListsMap.put(((SvnRevisionNumber)myRevision.getRevisionNumber()).getRevision().getNumber(), list);
      }
      return list;
    }

    public JComponent createOptionsPanel() {
      if (myPanel == null) {
        final SvnChangeList list = getList();
        if (list == null) {
          myPanel = new JPanel();
        }
        else {
          ChangeListViewerDialog dialog = new ChangeListViewerDialog(myProject, list);
          // TODO: Temporary memory leak fix - rewrite this part not to create dialog if only createCenterPanel(), but not show() is invoked
          Disposer.register(myProject, dialog.getDisposable());
          myPanel = dialog.createCenterPanel();
        }
      }
      return myPanel;
    }

    @Nls
    public String getDisplayName() {
      return getBannerSlogan();
    }

    public String getHelpTopic() {
      return null;
    }

    public boolean isModified() {
      return false;
    }

    public void apply() throws ConfigurationException {
    }

    public void reset() {
    }

    public void disposeUIResources() {
    }

    public SvnFileRevision getRevision() {
      return myRevision;
    }
  }

  private static class MyDialog extends DialogWrapper {
    private final Project myProject;
    private final SvnFileRevision myRevision;
    private final VirtualFile myFile;

    private MyDialog(final Project project, final SvnFileRevision revision, final VirtualFile file) {
      super(project, true);
      myProject = project;
      myRevision = revision;
      myFile = file;
      setTitle(SvnBundle.message("merge.source.details.title",
                                 (myFile == null) ? myRevision.getURL() : myFile.getName(),
                                 myRevision.getRevisionNumber().asString()));
      init();
    }

    public JComponent createCenterPanel() {
      final JComponent component = new SvnMergeSourceDetails(myProject, myRevision, myFile).createComponent();
      component.setMinimumSize(new Dimension(300, 200));
      return component;
    }
  }
}
