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
package org.jetbrains.idea.svn.history;

import consulo.logging.Logger;
import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.versionControlSystem.change.commited.VcsCommittedListsZipper;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;

import javax.annotation.Nonnull;

import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.commited.RepositoryLocationGroup;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNURL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
* @author Konstantin Kolosovsky.
*/
public class SvnCommittedListsZipper implements VcsCommittedListsZipper {

  private static final Logger LOG = Logger.getInstance(SvnCommittedListsZipper.class);

  @Nonnull
  private final SvnVcs myVcs;

  public SvnCommittedListsZipper(@Nonnull SvnVcs vcs) {
    myVcs = vcs;
  }

  public Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(final List<RepositoryLocation> in) {
    final List<RepositoryLocationGroup> groups = new ArrayList<>();
    final List<RepositoryLocation> singles = new ArrayList<>();

    final MultiMap<SVNURL, RepositoryLocation> map = new MultiMap<>();

    for (RepositoryLocation location : in) {
      final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
      final String url = svnLocation.getURL();

      final SVNURL root = SvnUtil.getRepositoryRoot(myVcs, url);
      if (root == null) {
        // should not occur
        LOG.info("repository root not found for location:"+ location.toPresentableString());
        singles.add(location);
      } else {
        map.putValue(root, svnLocation);
      }
    }

    final Set<SVNURL> keys = map.keySet();
    for (SVNURL key : keys) {
      final Collection<RepositoryLocation> repositoryLocations = map.get(key);
      if (repositoryLocations.size() == 1) {
        singles.add(repositoryLocations.iterator().next());
      } else {
        final SvnRepositoryLocationGroup group = new SvnRepositoryLocationGroup(key, repositoryLocations);
        groups.add(group);
      }
    }
    return Pair.create(groups, singles);
  }

  public CommittedChangeList zip(final RepositoryLocationGroup group, final List<CommittedChangeList> lists) {
    return new SvnChangeList(lists, new SvnRepositoryLocation(group.toPresentableString()));
  }

  public long getNumber(final CommittedChangeList list) {
    return list.getNumber();
  }
}
