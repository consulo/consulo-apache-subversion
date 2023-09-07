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
package org.jetbrains.idea.svn.mergeinfo;

import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.ui.VcsBalloonProblemNotifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class SvnMergeInfoCache {
  private final static Logger LOG = Logger.getInstance(SvnMergeInfoCache.class);

  @Nonnull
  private final Project myProject;
  // key - working copy root url
  @Nonnull
  private final Map<String, MyCurrentUrlData> myCurrentUrlMapping;

  @Deprecated
  public static Class<SvnMergeInfoCacheListener> SVN_MERGE_INFO_CACHE = SvnMergeInfoCacheListener.class;

  @Inject
  private SvnMergeInfoCache(@Nonnull Project project) {
    myProject = project;
    myCurrentUrlMapping = new HashMap<>();
  }

  public static SvnMergeInfoCache getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, SvnMergeInfoCache.class);
  }

  public void clear(@Nonnull WCInfoWithBranches info, String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    if (branchInfo != null) {
      branchInfo.clear();
    }
  }

  @Nullable
  public MergeInfoCached getCachedState(@Nonnull WCInfoWithBranches info, String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    return branchInfo != null ? branchInfo.getCached() : null;
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(@Nonnull WCInfoWithBranches info,
                                   @Nonnull SvnChangeList list,
                                   @Nonnull WCInfoWithBranches.Branch selectedBranch,
                                   final String branchPath) {
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getRootUrl());
    BranchInfo mergeChecker = null;
    if (rootMapping == null) {
      rootMapping = new MyCurrentUrlData();
      myCurrentUrlMapping.put(info.getRootUrl(), rootMapping);
    }
    else {
      mergeChecker = rootMapping.getBranchInfo(branchPath);
    }
    if (mergeChecker == null) {
      mergeChecker = new BranchInfo(SvnVcs.getInstance(myProject), info, selectedBranch);
      rootMapping.addBranchInfo(branchPath, mergeChecker);
    }

    return mergeChecker.checkList(list, branchPath);
  }

  public boolean isMixedRevisions(@Nonnull WCInfoWithBranches info, final String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    return branchInfo != null && branchInfo.isMixedRevisionsFound();
  }

  @Nullable
  private BranchInfo getBranchInfo(@Nonnull WCInfoWithBranches info, String branchPath) {
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getRootUrl());

    return rootMapping != null ? rootMapping.getBranchInfo(branchPath) : null;
  }

  public enum MergeCheckResult {
    COMMON,
    MERGED,
    NOT_MERGED,
    NOT_EXISTS;

    @Nonnull
    public static MergeCheckResult getInstance(boolean merged) {
      return merged ? MERGED : NOT_MERGED;
    }
  }

  static class CopyRevison {
    private final String myPath;
    private volatile long myRevision;

    CopyRevison(final SvnVcs vcs, final String path, @Nonnull SVNURL repositoryRoot, final String branchUrl, final String trunkUrl) {
      myPath = path;
      myRevision = -1;

      Task.Backgroundable task = new Task.Backgroundable(vcs.getProject(), "", false) {
        private CopyData myData;

        @Override
        public void run(@Nonnull ProgressIndicator indicator) {
          try {
            myData = new FirstInBranch(vcs, repositoryRoot, branchUrl, trunkUrl).run();
          }
          catch (VcsException e) {
            logAndShow(e);
          }
        }

        @Override
        public void onSuccess() {
          if (!vcs.getProject().isDisposed() && myData != null && myData.getCopySourceRevision() != -1) {
            vcs.getProject().getMessageBus().syncPublisher(SVN_MERGE_INFO_CACHE).copyRevisionUpdated();
          }
        }

        @Override
        public void onThrowable(@Nonnull Throwable error) {
          logAndShow(error);
        }

        private void logAndShow(@Nonnull Throwable error) {
          LOG.info(error);
          VcsBalloonProblemNotifier.showOverChangesView(vcs.getProject(), error.getMessage(), NotificationType.ERROR);
        }
      };
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new EmptyProgressIndicator());
    }

    public String getPath() {
      return myPath;
    }

    public long getRevision() {
      return myRevision;
    }
  }

  private static class MyCurrentUrlData {

    // key - working copy local path
    @Nonnull
    private final Map<String, BranchInfo> myBranchInfo;

    private MyCurrentUrlData() {
      myBranchInfo = ContainerUtil.createSoftMap();
    }

    public BranchInfo getBranchInfo(final String branchUrl) {
      return myBranchInfo.get(branchUrl);
    }

    public void addBranchInfo(@Nonnull String branchUrl, @Nonnull BranchInfo mergeChecker) {
      myBranchInfo.put(branchUrl, mergeChecker);
    }
  }

}
