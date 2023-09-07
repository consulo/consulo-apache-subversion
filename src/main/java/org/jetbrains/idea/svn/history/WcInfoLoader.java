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

import consulo.util.lang.ref.Ref;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.util.collection.ContainerUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.util.lang.Comparing;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.branchConfig.SvnBranchItem;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WcInfoLoader {

  @Nonnull
  private final SvnVcs myVcs;
  /**
   * filled when showing for selected location
   */
  @Nullable private final RepositoryLocation myLocation;

  public WcInfoLoader(@Nonnull SvnVcs vcs, @Nullable RepositoryLocation location) {
    myVcs = vcs;
    myLocation = location;
  }

  @Nonnull
  public List<WCInfoWithBranches> loadRoots() {
    List<WCInfoWithBranches> result = ContainerUtil.newArrayList();

    for (WCInfo info : myVcs.getAllWcInfos()) {
      ContainerUtil.addIfNotNull(result, createInfo(info));
    }

    return result;
  }

  @Nullable
  public WCInfoWithBranches reloadInfo(@Nonnull WCInfoWithBranches info) {
    File file = info.getRootInfo().getIoFile();
    RootUrlInfo rootInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(file);

    return rootInfo != null ? createInfo(new WCInfo(rootInfo, SvnUtil.isWorkingCopyRoot(file), SvnUtil.getDepth(myVcs, file))) : null;
  }

  @Nullable
  private WCInfoWithBranches createInfo(@Nonnull WCInfo info) {
    if (!info.getFormat().supportsMergeInfo()) {
      return null;
    }

    final String url = info.getUrl().toString();
    if (myLocation != null && !myLocation.toPresentableString().startsWith(url) && !url.startsWith(myLocation.toPresentableString())) {
      return null;
    }
    if (!SvnUtil.checkRepositoryVersion15(myVcs, url)) {
      return null;
    }

    // check of WC version
    RootUrlInfo rootForUrl = myVcs.getSvnFileUrlMapping().getWcRootForUrl(url);
    return rootForUrl != null ? createInfoWithBranches(info, rootForUrl) : null;
  }

  @Nonnull
  private WCInfoWithBranches createInfoWithBranches(@Nonnull WCInfo info, @Nonnull RootUrlInfo rootUrlInfo) {
    SvnBranchConfigurationNew configuration =
      SvnBranchConfigurationManager.getInstance(myVcs.getProject()).get(rootUrlInfo.getVirtualFile());
    Ref<WCInfoWithBranches.Branch> workingCopyBranch = Ref.create();
    List<WCInfoWithBranches.Branch> branches = ContainerUtil.newArrayList();
    String url = info.getUrl().toString();

    // TODO: Probably could utilize SvnBranchConfigurationNew.UrlListener and SvnBranchConfigurationNew.iterateUrls() behavior
    String trunkUrl = configuration.getTrunkUrl();
    if (trunkUrl != null) {
      add(url, trunkUrl, branches, workingCopyBranch);
    }

    for (String branchUrl : configuration.getBranchUrls()) {
      for (SvnBranchItem branchItem : configuration.getBranches(branchUrl)) {
        add(url, branchItem.getUrl(), branches, workingCopyBranch);
      }
    }

    Collections.sort(branches, new Comparator<WCInfoWithBranches.Branch>() {
      public int compare(final WCInfoWithBranches.Branch o1, final WCInfoWithBranches.Branch o2) {
        return Comparing.compare(o1.getUrl(), o2.getUrl());
      }
    });

    return new WCInfoWithBranches(info, branches, rootUrlInfo.getRoot(), workingCopyBranch.get());
  }

  private static void add(@Nonnull String url,
                          @Nonnull String branchUrl,
                          @Nonnull List<WCInfoWithBranches.Branch> branches,
                          @Nonnull Ref<WCInfoWithBranches.Branch> workingCopyBranch) {
    WCInfoWithBranches.Branch branch = new WCInfoWithBranches.Branch(branchUrl);

    if (!SVNPathUtil.isAncestor(branchUrl, url)) {
      branches.add(branch);
    }
    else {
      workingCopyBranch.set(branch);
    }
  }
}
