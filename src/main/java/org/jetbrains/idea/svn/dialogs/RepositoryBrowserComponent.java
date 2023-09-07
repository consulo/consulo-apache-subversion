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
package org.jetbrains.idea.svn.dialogs;

import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.ide.impl.idea.openapi.util.Disposer;
import consulo.ide.impl.idea.openapi.vcs.vfs.VcsFileSystem;
import consulo.ide.impl.idea.openapi.vcs.vfs.VcsVirtualFile;
import consulo.ide.impl.idea.util.NotNullFunction;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.language.editor.CommonDataKeys;
import consulo.language.file.FileTypeManager;
import consulo.navigation.NavigatableAdapter;
import consulo.project.Project;
import consulo.ui.ex.awt.EditSourceOnDoubleClickHandler;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.speedSearch.SpeedSearchComparator;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.Tree;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/**
 * @author alex
 */
public class RepositoryBrowserComponent extends JPanel implements Disposable, DataProvider {

  private JTree myRepositoryTree;
  private final SvnVcs myVCS;

  public RepositoryBrowserComponent(@Nonnull SvnVcs vcs) {
    myVCS = vcs;
    createComponent();
  }

  public JTree getRepositoryTree() {
    return myRepositoryTree;
  }

  @Nonnull
  public Project getProject() {
    return myVCS.getProject();
  }

  public void setRepositoryURLs(SVNURL[] urls, final boolean showFiles) {
    setRepositoryURLs(urls, showFiles, null, false);
  }

  public void setRepositoryURLs(SVNURL[] urls,
                                final boolean showFiles,
                                @Nullable NotNullFunction<RepositoryBrowserComponent, Expander> defaultExpanderFactory,
                                boolean expandFirst) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);

    if (defaultExpanderFactory != null) {
      model.setDefaultExpanderFactory(defaultExpanderFactory);
    }

    model.setRoots(urls);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);

    if (expandFirst) {
      myRepositoryTree.expandRow(0);
    }
  }

  public void setRepositoryURL(SVNURL url,
                               boolean showFiles,
                               final NotNullFunction<RepositoryBrowserComponent, Expander> defaultExpanderFactory) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);

    model.setDefaultExpanderFactory(defaultExpanderFactory);

    model.setSingleRoot(url);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
  }

  public void setRepositoryURL(SVNURL url, boolean showFiles) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);
    model.setSingleRoot(url);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
  }

  public void expandNode(@Nonnull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(treeNode);

    if ((pathToNode != null) && (pathToNode.length > 0)) {
      final TreePath treePath = new TreePath(pathToNode);
      myRepositoryTree.expandPath(treePath);
    }
  }

  public Collection<TreeNode> getExpandedSubTree(@Nonnull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(treeNode);

    final Enumeration<TreePath> expanded = myRepositoryTree.getExpandedDescendants(new TreePath(pathToNode));

    final List<TreeNode> result = new ArrayList<>();
    if (expanded != null) {
      while (expanded.hasMoreElements()) {
        final TreePath treePath = expanded.nextElement();
        result.add((TreeNode)treePath.getLastPathComponent());
      }
    }
    return result;
  }

  public boolean isExpanded(@Nonnull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(treeNode);

    return (pathToNode != null) && (pathToNode.length > 0) && myRepositoryTree.isExpanded(new TreePath(pathToNode));
  }

  public void addURL(String url) {
    try {
      ((RepositoryTreeModel)myRepositoryTree.getModel()).addRoot(SVNURL.parseURIEncoded(url));
    }
    catch (SVNException e) {
      //
    }
  }

  public void removeURL(String url) {
    try {
      ((RepositoryTreeModel)myRepositoryTree.getModel()).removeRoot(SVNURL.parseURIEncoded(url));
    }
    catch (SVNException e) {
      //
    }
  }

  @Nullable
  public DirectoryEntry getSelectedEntry() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode)element;
      return node.getSVNDirEntry();
    }
    return null;
  }

  @Nullable
  public String getSelectedURL() {
    SVNURL selectedUrl = getSelectedSVNURL();
    return selectedUrl == null ? null : selectedUrl.toString();
  }

  @Nullable
  public SVNURL getSelectedSVNURL() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode)element;
      return node.getURL();
    }
    return null;
  }

  public void addChangeListener(TreeSelectionListener l) {
    myRepositoryTree.addTreeSelectionListener(l);
  }

  public void removeChangeListener(TreeSelectionListener l) {
    myRepositoryTree.removeTreeSelectionListener(l);
  }

  public Component getPreferredFocusedComponent() {
    return myRepositoryTree;
  }

  private void createComponent() {
    setLayout(new BorderLayout());
    myRepositoryTree = new Tree();
    myRepositoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myRepositoryTree.setRootVisible(false);
    myRepositoryTree.setShowsRootHandles(true);
    JScrollPane scrollPane =
      ScrollPaneFactory.createScrollPane(myRepositoryTree, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    add(scrollPane, BorderLayout.CENTER);
    myRepositoryTree.setCellRenderer(new SvnRepositoryTreeCellRenderer());
    TreeSpeedSearch search = new TreeSpeedSearch(myRepositoryTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        Object component = o.getLastPathComponent();
        if (component instanceof RepositoryTreeNode) {
          return ((RepositoryTreeNode)component).getURL().toDecodedString();
        }
        return null;
      }
    });
    search.setComparator(new SpeedSearchComparator(false, true));

    EditSourceOnDoubleClickHandler.install(myRepositoryTree);
  }

  @Nullable
  public RepositoryTreeNode getSelectedNode() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection != null && selection.getLastPathComponent() instanceof RepositoryTreeNode) {
      return (RepositoryTreeNode)selection.getLastPathComponent();
    }
    return null;
  }

  public void setSelectedNode(@Nonnull final TreeNode node) {
    final TreeNode[] pathNodes = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(node);
    myRepositoryTree.setSelectionPath(new TreePath(pathNodes));
  }

  @Nullable
  public VirtualFile getSelectedVcsFile() {
    final RepositoryTreeNode node = getSelectedNode();
    if (node == null) return null;

    DirectoryEntry entry = node.getSVNDirEntry();
    if (entry == null || !entry.isFile()) {
      return null;
    }

    String name = entry.getName();
    FileTypeManager manager = FileTypeManager.getInstance();

    if (entry.getName().lastIndexOf('.') > 0 && !manager.getFileTypeByFileName(name).isBinary()) {
      SVNURL url = node.getURL();
      final SvnFileRevision revision = new SvnFileRevision(myVCS, SVNRevision.UNDEFINED, SVNRevision.HEAD, url.toString(),
                                                           entry.getAuthor(), entry.getDate(), null, null);

      return new VcsVirtualFile(node.getSVNDirEntry().getName(), revision, VcsFileSystem.getInstance());
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public Object getData(Key<?> dataId) {
    if (CommonDataKeys.NAVIGATABLE == dataId) {
      final Project project = myVCS.getProject();
      if (project == null || project.isDefault()) {
        return null;
      }
      final VirtualFile vcsFile = getSelectedVcsFile();

      // do not return OpenFileDescriptor instance here as in that case SelectInAction will be enabled and its invocation (using keyboard)
      // will raise error - see IDEA-104113 - because of the following operations inside SelectInAction.actionPerformed():
      // - at first VcsVirtualFile content will be loaded which for svn results in showing progress dialog
      // - then DataContext from SelectInAction will still be accessed which results in error as current event count has already changed
      // (because of progress dialog)
      return vcsFile != null ? new NavigatableAdapter() {
        @Override
        public void navigate(boolean requestFocus) {
          navigate(project, vcsFile, requestFocus);
        }
      } : null;
    }
    else if (CommonDataKeys.PROJECT == dataId) {
      return myVCS.getProject();
    }
    return null;
  }

  public void dispose() {
  }

  public void setLazyLoadingExpander(final NotNullFunction<RepositoryBrowserComponent, Expander> expanderFactory) {
    ((RepositoryTreeModel)myRepositoryTree.getModel()).setDefaultExpanderFactory(expanderFactory);
  }
}
