/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.ui.ModalityState;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.auth.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.*;

class RepositoryLoader extends Loader {
  // may be several requests if: several same-level nodes are expanded simultaneosly; or browser can be opening into some expanded state
  @Nonnull
  private final Queue<Pair<RepositoryTreeNode, Expander>> myLoadQueue;
  private boolean myQueueProcessorActive;

  RepositoryLoader(@Nonnull SvnRepositoryCache cache) {
    super(cache);

    myLoadQueue = new LinkedList<>();
    myQueueProcessorActive = false;
  }

  public void load(@Nonnull RepositoryTreeNode node, @Nonnull Expander afterRefreshExpander) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = Pair.create(node, afterRefreshExpander);
    if (!myQueueProcessorActive) {
      startLoadTask(data);
      myQueueProcessorActive = true;
    }
    else {
      myLoadQueue.offer(data);
    }
  }

  private void setResults(@Nonnull Pair<RepositoryTreeNode, Expander> data, @Nonnull List<DirectoryEntry> children) {
    myCache.put(data.first.getURL().toString(), children);
    refreshNode(data.first, children, data.second);
  }

  private void setError(@Nonnull Pair<RepositoryTreeNode, Expander> data, @Nonnull String message) {
    myCache.put(data.first.getURL().toString(), message);
    refreshNodeError(data.first, message);
  }

  private void startNext() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = myLoadQueue.poll();
    if (data == null) {
      myQueueProcessorActive = false;
      return;
    }
    if (data.first.isDisposed()) {
      // ignore if node is already disposed
      startNext();
    }
    else {
      startLoadTask(data);
    }
  }

  private void startLoadTask(@Nonnull final Pair<RepositoryTreeNode, Expander> data) {
    Application application = ApplicationManager.getApplication();
    final ModalityState state = application.getCurrentModalityState();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().runProcess(new LoadTask(data), new EmptyProgressIndicator(state));
      }
    });
  }

  @Nonnull
  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.REFRESHED;
  }

  private class LoadTask implements Runnable {

    @Nonnull
    private final Pair<RepositoryTreeNode, Expander> myData;

    private LoadTask(@Nonnull Pair<RepositoryTreeNode, Expander> data) {
      myData = data;
    }

    public void run() {
      final Collection<DirectoryEntry> entries = new TreeSet<>();
      final RepositoryTreeNode node = myData.first;
      final SvnVcs vcs = node.getVcs();
      SvnAuthenticationProvider.forceInteractive();

      DirectoryEntryConsumer handler = new DirectoryEntryConsumer() {

        @Override
        public void consume(final DirectoryEntry entry) throws SVNException {
          entries.add(entry);
        }
      };
      try {
        SvnTarget target = SvnTarget.fromURL(node.getURL());
        vcs.getFactoryFromSettings().createBrowseClient().list(target, SVNRevision.HEAD, Depth.IMMEDIATES, handler);
      }
      catch (final VcsException e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            setError(myData, e.getMessage());
            startNext();
          }
        });
        return;
      }
      finally {
        SvnAuthenticationProvider.clearInteractive();
      }

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          setResults(myData, ContainerUtil.newArrayList(entries));
          startNext();
        }
      });
    }
  }
}
