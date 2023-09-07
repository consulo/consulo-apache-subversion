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

import consulo.application.ApplicationManager;
import consulo.component.messagebus.MessageBusConnection;
import consulo.ide.impl.idea.openapi.util.ZipperUpdater;
import consulo.ide.impl.idea.openapi.vcs.changes.committed.CommittedChangesReloadListener;
import consulo.ide.impl.idea.openapi.vcs.changes.committed.VcsBranchMappingChangedNotification;
import consulo.project.Project;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.CommittedChangesMergedStateChanged;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCacheListener;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static consulo.versionControlSystem.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED;

public class MergeInfoUpdatesListener {
  private final static int DELAY = 300;

  private final Project myProject;
  private final MessageBusConnection myConnection;
  private List<RootsAndBranches> myMergeInfoRefreshActions;
  private final ZipperUpdater myUpdater;

  public MergeInfoUpdatesListener(@Nonnull Project project, final MessageBusConnection connection) {
    myConnection = connection;
    myProject = project;
    myUpdater = new ZipperUpdater(DELAY, project);
  }

  public void addPanel(final RootsAndBranches action) {
    if (myMergeInfoRefreshActions == null) {
      myMergeInfoRefreshActions = new ArrayList<>();
      myMergeInfoRefreshActions.add(action);

      myConnection.subscribe(VcsBranchMappingChangedNotification.class, new VcsBranchMappingChangedNotification() {
        public void execute(final Project project, final VirtualFile vcsRoot) {
          callReloadMergeInfo();
        }
      });
      final Runnable reloadRunnable = this::callReloadMergeInfo;
      myConnection.subscribe(SvnVcs.WC_CONVERTED, reloadRunnable::run);
      myConnection.subscribe(RootsAndBranches.REFRESH_REQUEST, reloadRunnable::run);

      myConnection.subscribe(SvnVcs.ROOTS_RELOADED, mappingChanged -> {
        if (mappingChanged) {
          callReloadMergeInfo();
        }
      });

      myConnection.subscribe(VCS_CONFIGURATION_CHANGED, () -> callReloadMergeInfo());

      myConnection.subscribe(CommittedChangesReloadListener.class, new CommittedChangesReloadListener() {
        public void itemsReloaded() {
          reloadRunnable.run();
        }

        public void emptyRefresh() {
        }
      });

      myConnection.subscribe(SvnMergeInfoCacheListener.class, new SvnMergeInfoCacheListener() {
        public void copyRevisionUpdated() {
          doForEachInitialized(rootsAndBranches -> rootsAndBranches.fireRepaint());
        }
      });

      myConnection.subscribe(Merger.COMMITTED_CHANGES_MERGED_STATE, new CommittedChangesMergedStateChanged() {
        public void event(final List<CommittedChangeList> list) {
          doForEachInitialized(rootsAndBranches -> rootsAndBranches.refreshByLists(list));
        }
      });
    }
    else {
      myMergeInfoRefreshActions.add(action);
    }
  }

  private void doForEachInitialized(final Consumer<RootsAndBranches> consumer) {
    myUpdater.queue(new Runnable() {
      public void run() {
        for (final RootsAndBranches action : myMergeInfoRefreshActions) {
          if (action.strategyInitialized()) {
            if (ApplicationManager.getApplication().isDispatchThread()) {
              consumer.accept(action);
            }
            else {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  consumer.accept(action);
                }
              });
            }
          }
        }
      }
    });
  }

  private void callReloadMergeInfo() {
    doForEachInitialized(new Consumer<RootsAndBranches>() {
      public void accept(final RootsAndBranches rootsAndBranches) {
        rootsAndBranches.reloadPanels();
        rootsAndBranches.refresh();
      }
    });
  }

  public void removePanel(final RootsAndBranches action) {
    if (myMergeInfoRefreshActions != null) {
      myMergeInfoRefreshActions.remove(action);
    }
  }
}
