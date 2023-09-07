/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.branchConfig;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.ArrayList;

/**
* @author Konstantin Kolosovsky.
*/
public class DefaultBranchConfigInitializer implements Runnable {

  private static final Logger LOG = Logger.getInstance(DefaultBranchConfigInitializer.class);

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  @Nonnull
  private final Project myProject;
  @Nonnull
  private final NewRootBunch myBunch;
  @Nonnull
  private final VirtualFile myRoot;

  public DefaultBranchConfigInitializer(@Nonnull Project project, @Nonnull NewRootBunch bunch, @Nonnull VirtualFile root) {
    myProject = project;
    myRoot = root;
    myBunch = bunch;
  }

  public void run() {
    SvnBranchConfigurationNew configuration = getDefaultConfiguration();

    if (configuration != null) {
      for (String url : configuration.getBranchUrls()) {
        myBunch.reloadBranchesAsync(myRoot, url, InfoReliability.defaultValues);
      }

      myBunch.updateForRoot(myRoot, new InfoStorage<>(configuration, InfoReliability.defaultValues), false);
    }
  }

  @Nullable
  public SvnBranchConfigurationNew getDefaultConfiguration() {
    SvnBranchConfigurationNew result = null;
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    SVNURL rootUrl = SvnUtil.getUrl(vcs, VfsUtilCore.virtualToIoFile(myRoot));

    if (rootUrl != null) {
      try {
        result = getDefaultConfiguration(vcs, rootUrl);
      }
      catch (SVNException | VcsException e) {
        LOG.info(e);
      }
    }
    else {
      LOG.info("Directory is not a working copy: " + myRoot.getPresentableUrl());
    }

    return result;
  }

  @Nonnull
  private static SvnBranchConfigurationNew getDefaultConfiguration(@Nonnull SvnVcs vcs, @Nonnull SVNURL url)
    throws SVNException, VcsException {
    SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
    result.setTrunkUrl(url.toString());

    SVNURL branchLocationsParent = getBranchLocationsParent(url);
    if (branchLocationsParent != null) {
      SvnTarget target = SvnTarget.fromURL(branchLocationsParent);

      vcs.getFactory(target).createBrowseClient().list(target, SVNRevision.HEAD, Depth.IMMEDIATES, createHandler(result, target.getURL()));
    }

    return result;
  }

  @Nullable
  private static SVNURL getBranchLocationsParent(@Nonnull SVNURL url) throws SVNException {
    while (!hasEmptyName(url) && !hasDefaultName(url)) {
      url = url.removePathTail();
    }

    return hasDefaultName(url) ? url.removePathTail() : null;
  }

  private static boolean hasEmptyName(@Nonnull SVNURL url) {
    return StringUtil.isEmpty(SVNPathUtil.tail(url.getPath()));
  }

  private static boolean hasDefaultName(@Nonnull SVNURL url) {
    String name = SVNPathUtil.tail(url.getPath());

    return name.equalsIgnoreCase(DEFAULT_TRUNK_NAME) ||
           name.equalsIgnoreCase(DEFAULT_BRANCHES_NAME) ||
           name.equalsIgnoreCase(DEFAULT_TAGS_NAME);
  }

  @Nonnull
  private static DirectoryEntryConsumer createHandler(@Nonnull final SvnBranchConfigurationNew result, @Nonnull final SVNURL rootPath) {
    return new DirectoryEntryConsumer() {

      @Override
      public void consume(final DirectoryEntry entry) throws SVNException {
        if (entry.isDirectory()) {
          SVNURL childUrl = rootPath.appendPath(entry.getName(), false);

          if (StringUtil.endsWithIgnoreCase(entry.getName(), DEFAULT_TRUNK_NAME)) {
            result.setTrunkUrl(childUrl.toString());
          }
          else {
            result.addBranches(childUrl.toString(),
                               new InfoStorage<>(new ArrayList<>(0), InfoReliability.defaultValues));
          }
        }
      }
    };
  }
}
