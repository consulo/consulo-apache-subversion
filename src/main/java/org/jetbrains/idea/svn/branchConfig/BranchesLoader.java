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

import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.ui.VcsBalloonProblemNotifier;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class BranchesLoader implements Runnable {
  @Nonnull
  private final Project myProject;
  @Nonnull
  private final NewRootBunch myBunch;
  @Nonnull
  private final VirtualFile myRoot;
  @Nonnull
  private final String myUrl;
  @Nonnull
  private final InfoReliability myInfoReliability;
  private final boolean myPassive;

  public BranchesLoader(@Nonnull Project project,
                        @Nonnull NewRootBunch bunch,
                        @Nonnull String url,
                        @Nonnull InfoReliability infoReliability,
                        @Nonnull VirtualFile root,
                        boolean passive) {
    myProject = project;
    myBunch = bunch;
    myUrl = url;
    myInfoReliability = infoReliability;
    myRoot = root;
    myPassive = passive;
  }

  public void run() {
    try {
      List<SvnBranchItem> branches = loadBranches();
      myBunch.updateBranches(myRoot, myUrl, new InfoStorage<>(branches, myInfoReliability));
    }
    catch (VcsException | SVNException e) {
      showError(e);
    }
  }

  @Nonnull
  public List<SvnBranchItem> loadBranches() throws SVNException, VcsException
  {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    SVNURL branchesUrl = SVNURL.parseURIEncoded(myUrl);
    List<SvnBranchItem> result = new LinkedList<>();
    SvnTarget target = SvnTarget.fromURL(branchesUrl);
    DirectoryEntryConsumer handler = createConsumer(result);

    vcs.getFactory(target).create(BrowseClient.class, !myPassive).list(target, SVNRevision.HEAD, Depth.IMMEDIATES, handler);

    Collections.sort(result);
    return result;
  }

  private void showError(Exception e) {
    // already logged inside
    if (InfoReliability.setByUser.equals(myInfoReliability)) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Branches load error: " + e.getMessage(), NotificationType.ERROR);
    }
  }

  @Nonnull
  private static DirectoryEntryConsumer createConsumer(@Nonnull final List<SvnBranchItem> result) {
    return new DirectoryEntryConsumer() {

      @Override
      public void consume(final DirectoryEntry entry) throws SVNException {
        if (entry.getDate() != null) {
          result.add(new SvnBranchItem(entry.getUrl().toDecodedString(), entry.getDate(), entry.getRevision()));
        }
      }
    };
  }
}
