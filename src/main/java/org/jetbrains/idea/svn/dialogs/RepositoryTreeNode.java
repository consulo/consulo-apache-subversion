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

import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.disposer.Disposable;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.FilteringIterator;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.NodeLoadState;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.function.Function;

public class RepositoryTreeNode implements TreeNode, Disposable {

  private TreeNode myParentNode;
  @Nonnull
  private final List<TreeNode> myChildren;
  private final RepositoryTreeModel myModel;
  private final SVNURL myURL;
  private final Object myUserObject;

  @Nonnull
  private final NodeLoadState myLoadState;
  private NodeLoadState myChildrenLoadState;

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode,
                            @Nonnull SVNURL url, Object userObject, @Nonnull NodeLoadState state) {
    myParentNode = parentNode;

    myURL = url;
    myModel = model;
    myUserObject = userObject;

    myLoadState = state;
    myChildren = ContainerUtil.newArrayList();
    myChildrenLoadState = NodeLoadState.EMPTY;
  }

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode, @Nonnull SVNURL url, Object userObject) {
    // created outside: only roots
    this(model, parentNode, url, userObject, NodeLoadState.REFRESHED);
  }

  public Object getUserObject() {
    return myUserObject;
  }

  public int getChildCount() {
    return getChildren().size();
  }

  public Enumeration children() {
    return Collections.enumeration(getChildren());
  }

  public TreeNode getChildAt(int childIndex) {
    return (TreeNode)getChildren().get(childIndex);
  }

  public int getIndex(TreeNode node) {
    return getChildren().indexOf(node);
  }

  public boolean getAllowsChildren() {
    return !isLeaf();
  }

  public boolean isLeaf() {
    return myUserObject instanceof DirectoryEntry && ((DirectoryEntry)myUserObject).isFile();
  }

  public TreeNode getParent() {
    return myParentNode;
  }

  public void reload(final boolean removeCurrentChildren) {
    // todo lazyLoading as explicit: keeping...
    reload(removeCurrentChildren ? myModel.getSelectionKeepingExpander() : myModel.getLazyLoadingExpander(), removeCurrentChildren);
  }

  @Nullable
  public TreeNode getNextChildByKey(final String key, final boolean isFolder) {
    final ByKeySelectedSearcher searcher = (isFolder) ? new FolderByKeySelectedSearcher(key, myChildren) :
      new FileByKeySelectedSearcher(key, myChildren);
    return searcher.getNextSelectedByKey();
  }

  public String toString() {
    if (myParentNode instanceof RepositoryTreeRootNode) {
      return myURL.toString();
    }
    return SVNPathUtil.tail(myURL.getPath());
  }

  public void reload(@Nonnull Expander expander, boolean removeCurrentChildren) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (removeCurrentChildren || NodeLoadState.EMPTY.equals(myChildrenLoadState)) {
      initChildren();
    }

    myModel.getCacheLoader().load(this, expander);
  }

  private void initChildren() {
    myChildren.clear();
    myChildren.add(new SimpleTextNode(CommonBundle.getLoadingTreeNodeText()));
    myChildrenLoadState = NodeLoadState.LOADING;
  }

  private List getChildren() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (NodeLoadState.EMPTY.equals(myChildrenLoadState)) {
      initChildren();
      myModel.getCacheLoader().load(this, myModel.getLazyLoadingExpander());
    }
    return myChildren;
  }

  public SVNURL getURL() {
    return myURL;
  }

  @Nullable
  public DirectoryEntry getSVNDirEntry() {
    return myUserObject instanceof DirectoryEntry ? (DirectoryEntry)myUserObject : null;
  }

  public void dispose() {
  }

  public TreeNode[] getSelfPath() {
    return myModel.getPathToRoot(this);
  }

  public boolean isRepositoryRoot() {
    return !(myUserObject instanceof DirectoryEntry);
  }

  @Nonnull
  public List<TreeNode> getAllAlreadyLoadedChildren() {
    return ContainerUtil.newArrayList(myChildren);
  }

  @Nonnull
  public List<RepositoryTreeNode> getAlreadyLoadedChildren() {
    return myChildren.stream().map(treeNode -> ObjectUtil.tryCast(treeNode, RepositoryTreeNode.class))
                     .filter(Objects::nonNull)
                     .toList();
  }

  public boolean isDisposed() {
    return myModel.isDisposed();
  }

  public void setChildren(@Nonnull List<DirectoryEntry> children, @Nonnull NodeLoadState state) {
    final List<TreeNode> nodes = new ArrayList<>();
    for (final DirectoryEntry entry : children) {
      if (!myModel.isShowFiles() && !entry.isDirectory()) {
        continue;
      }
      nodes.add(new RepositoryTreeNode(myModel, this, entry.getUrl(), entry, state));
    }

    myChildrenLoadState = state;
    myChildren.clear();
    myChildren.addAll(nodes);

    myModel.reload(this);
  }

  public void setParentNode(final TreeNode parentNode) {
    myParentNode = parentNode;
  }

  public void setAlienChildren(final List<TreeNode> children, final NodeLoadState oldState) {
    myChildren.clear();

    for (TreeNode child : children) {
      if (child instanceof RepositoryTreeNode) {
        ((RepositoryTreeNode)child).setParentNode(this);
        myChildren.add(child);
        myChildrenLoadState = oldState;
      }
      else if (child instanceof SimpleTextNode) {
        SimpleTextNode node = (SimpleTextNode)child;
        myChildren.add(new SimpleTextNode(node.getText(), node.isError()));
        myChildrenLoadState = oldState;
      }
    }

    myModel.reload(this);
  }

  public void setErrorNode(@Nonnull String text) {
    myChildren.clear();
    myChildren.add(new SimpleTextNode(text, true));
    myChildrenLoadState = NodeLoadState.ERROR;
    myModel.reload(this);
  }

  public SvnVcs getVcs() {
    return myModel.getVCS();
  }

  public boolean isCached() {
    return NodeLoadState.CACHED.equals(myLoadState);
  }

  @Nullable
  public RepositoryTreeNode getNodeWithSamePathUnderModelRoot() {
    return myModel.findByUrl(this);
  }

  public NodeLoadState getChildrenLoadState() {
    return myChildrenLoadState;
  }

  public void doOnSubtree(@Nonnull Function<RepositoryTreeNode, Object> function) {
    new SubTreeWalker(this, function).execute();
  }

  private static class SubTreeWalker {

    @Nonnull
    private final RepositoryTreeNode myNode;
    @Nonnull
    private final Function<RepositoryTreeNode, Object> myFunction;

    private SubTreeWalker(@Nonnull RepositoryTreeNode node, @Nonnull Function<RepositoryTreeNode, Object> function) {
      myNode = node;
      myFunction = function;
    }

    public void execute() {
      executeImpl(myNode);
    }

    private void executeImpl(final RepositoryTreeNode node) {
      myFunction.apply(node);
      for (RepositoryTreeNode child : node.getAlreadyLoadedChildren()) {
        myFunction.apply(child);
      }
    }
  }
}
