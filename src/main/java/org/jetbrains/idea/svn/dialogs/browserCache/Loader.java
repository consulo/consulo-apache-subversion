/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs.browserCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import java.util.List;

public abstract class Loader {

  @Nonnull
  protected final SvnRepositoryCache myCache;

  protected Loader(@Nonnull SvnRepositoryCache cache) {
    myCache = cache;
  }

  public abstract void load(@Nonnull RepositoryTreeNode node, @Nonnull Expander afterRefreshExpander);

  @Nonnull
  protected abstract NodeLoadState getNodeLoadState();

  protected void refreshNodeError(@Nonnull RepositoryTreeNode node, @Nonnull String text) {
    RepositoryTreeNode existingNode = findExistingNode(node);

    if (existingNode != null) {
      existingNode.setErrorNode(text);
    }
  }

  protected void refreshNode(@Nonnull RepositoryTreeNode node, @Nonnull List<DirectoryEntry> data, @Nonnull Expander expander) {
    RepositoryTreeNode existingNode = findExistingNode(node);

    if (existingNode != null) {
      expander.onBeforeRefresh(existingNode);
      existingNode.setChildren(data, getNodeLoadState());
      expander.onAfterRefresh(existingNode);
    }
  }

  @Nullable
  private static RepositoryTreeNode findExistingNode(@Nonnull RepositoryTreeNode node) {
    RepositoryTreeNode result = null;

    if (!node.isDisposed()) {
      result = node.getNodeWithSamePathUnderModelRoot();
    }

    return result == null || result.isDisposed() ? null : result;
  }
}
