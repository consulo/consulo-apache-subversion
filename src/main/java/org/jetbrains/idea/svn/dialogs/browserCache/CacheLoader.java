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

import java.util.List;

import javax.annotation.Nonnull;

import consulo.ide.ServiceManager;
import jakarta.inject.Singleton;
import javax.swing.SwingUtilities;

import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

@Singleton
public class CacheLoader extends Loader {
  @Nonnull
  private final Loader myRepositoryLoader;

  public static Loader getInstance() {
    return ServiceManager.getService(Loader.class);
  }

  public CacheLoader() {
    super(SvnRepositoryCache.getInstance());
    myRepositoryLoader = new RepositoryLoader(myCache);
  }

  public void load(@Nonnull final RepositoryTreeNode node, @Nonnull final Expander expander) {
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        final String nodeUrl = node.getURL().toString();

        final List<DirectoryEntry> cached = myCache.getChildren(nodeUrl);
        if (cached != null) {
          refreshNode(node, cached, expander);
        }
        final String error = myCache.getError(nodeUrl);
        if (error != null) {
          refreshNodeError(node, error);
        }
        // refresh anyway
        myRepositoryLoader.load(node, expander);
      }
    });
  }

  @Nonnull
  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.CACHED;
  }
}
