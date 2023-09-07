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
package org.jetbrains.idea.svn.integrate;

import consulo.ide.impl.idea.openapi.vcs.changes.EmptyChangelistBuilder;
import consulo.ide.impl.idea.openapi.vcs.update.UpdatedFilesReverseSide;
import consulo.logging.Logger;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsKey;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

public class GatheringChangelistBuilder extends EmptyChangelistBuilder {
  private static final Logger LOG = Logger.getInstance(GatheringChangelistBuilder.class);

  @Nonnull
  private final Set<VirtualFile> myCheckSet;
  @Nonnull
  private final List<Change> myChanges;
  @Nonnull
  private final UpdatedFilesReverseSide myFiles;
  @Nonnull
  private final SvnVcs myVcs;

  public GatheringChangelistBuilder(@Nonnull SvnVcs vcs, @Nonnull UpdatedFilesReverseSide files) {
    myVcs = vcs;
    myFiles = files;
    myChanges = new ArrayList<>();
    myCheckSet = new HashSet<>();
  }

  public void processChange(final Change change, VcsKey vcsKey) {
    addChange(change);
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList, VcsKey vcsKey) {
    addChange(change);
  }

  public void processChangeInList(final Change change, final String changeListName, VcsKey vcsKey) {
    addChange(change);
  }

  @Override
  public void removeRegisteredChangeFor(FilePath path) {
    // not sure
    for (Iterator<Change> iterator = myChanges.iterator(); iterator.hasNext(); ) {
      final Change change = iterator.next();
      if (path.equals(ChangesUtil.getFilePath(change))) {
        final VirtualFile vf = path.getVirtualFile();
        if (vf != null) {
          myCheckSet.remove(vf);
          iterator.remove();
          return;
        }
      }
    }
  }

  private void addChange(final Change change) {
    final FilePath path = ChangesUtil.getFilePath(change);
    final VirtualFile vf = path.getVirtualFile();
    if ((mergeInfoChanged(path.getIOFile()) || (vf != null && myFiles.containsFile(vf))) && !myCheckSet.contains(vf)) {
      myCheckSet.add(vf);
      myChanges.add(change);
    }
  }

  private boolean mergeInfoChanged(final File file) {
    SvnTarget target = SvnTarget.fromFile(file);

    try {
      PropertyValue current =
        myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, SVNRevision.WORKING);
      PropertyValue base =
        myVcs.getFactory(target).createPropertyClient().getProperty(target, SvnPropertyKeys.MERGE_INFO, false, SVNRevision.BASE);

      if (current != null) {
        return base == null || !Comparing.equal(current, base);
      }
    }
    catch (VcsException e) {
      LOG.info(e);
    }
    return false;
  }

  public boolean reportChangesOutsideProject() {
    return true;
  }

  @Nonnull
  public List<Change> getChanges() {
    return myChanges;
  }
}
