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
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.change.ChangeListClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.tmatesoft.svn.core.SVNErrorCode;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class SvnChangelistListener implements ChangeListListener {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangelistListener");

  @Nonnull
  private final SvnVcs myVcs;
  @Nonnull
  private final Condition<FilePath> myUnderSvnCondition;

  public SvnChangelistListener(@Nonnull SvnVcs vcs) {
    myVcs = vcs;
    myUnderSvnCondition = new Condition<FilePath>() {
      @Override
      public boolean value(@Nonnull FilePath path) {
        final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsFor(path);
        return vcs != null && SvnVcs.VCS_NAME.equals(vcs.getName());
      }
    };
  }

  public void changeListAdded(final ChangeList list) {
    // SVN change list exists only when there are any files in it
  }

  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
    if (LocalChangeList.DEFAULT_NAME.equals(fromList.getName())) {
      return;
    }
    removeFromChangeList(changes);
  }

  public void changesAdded(Collection<Change> changes, ChangeList toList) {
    if (toList == null || LocalChangeList.DEFAULT_NAME.equals(toList.getName())) {
      return;
    }
    addToChangeList(toList.getName(), changes);
  }

  public void changeListRemoved(final ChangeList list) {
    removeFromChangeList(list.getChanges());
  }

  @Nonnull
  private List<FilePath> getPathsFromChanges(@Nonnull Collection<Change> changes) {
    return ContainerUtil.findAll(ChangesUtil.getPaths(changes), myUnderSvnCondition);
  }

  public void changeListChanged(final ChangeList list) {
  }

  public void changeListRenamed(final ChangeList list, final String oldName) {
    if (Comparing.equal(list.getName(), oldName)) {
      return;
    }
    if (LocalChangeList.DEFAULT_NAME.equals(list.getName())) {
      changeListRemoved(list);
      return;
    }
    addToChangeList(list.getName(), list.getChanges());
  }

  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
  }

  public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
    if (fromList.getName().equals(toList.getName())) {
      return;
    }
    if (LocalChangeList.DEFAULT_NAME.equals(toList.getName())) {
      changeListRemoved(toList);
      return;
    }

    final String[] fromLists = LocalChangeList.DEFAULT_NAME.equals(fromList.getName()) ? null : new String[] {fromList.getName()};
    addToChangeList(toList.getName(), changes, fromLists);
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
  }

  @Nullable
  public static String getCurrentMapping(@Nonnull SvnVcs vcs, @Nonnull File file) {
    try {
      final Status status = vcs.getFactory(file).createStatusClient().doStatus(file, false);
      return status == null ? null : status.getChangelistName();
    }
    catch (SvnBindException e) {
      if (e.contains(SVNErrorCode.WC_NOT_DIRECTORY) || e.contains(SVNErrorCode.WC_NOT_FILE)) {
        LOG.debug("Logging only, exception is valid (caught) here", e);
      } else {
        LOG.info("Logging only, exception is valid (caught) here", e);
      }
    }
    return null;
  }

  public static void putUnderList(@Nonnull SvnVcs vcs, @Nonnull String list, @Nonnull File after) throws VcsException {
    doChangeListOperation(vcs, after, client -> client.add(list, after, null));
  }

  public static void removeFromList(@Nonnull SvnVcs vcs, @Nonnull File after) throws VcsException {
    doChangeListOperation(vcs, after, client -> client.remove(after));
  }

  private static void doChangeListOperation(@Nonnull SvnVcs vcs,
                                            @Nonnull File file,
                                            @Nonnull ThrowableConsumer<ChangeListClient, VcsException> operation) throws VcsException {
    try {
      operation.consume(vcs.getFactory(file).createChangeListClient());
    }
    catch (SvnBindException e) {
      LOG.info(e);
      if (!e.contains(SVNErrorCode.WC_NOT_DIRECTORY) && !e.contains(SVNErrorCode.WC_NOT_FILE)) {
        throw e;
      }
    }
    catch (VcsException e) {
      LOG.info(e);
      throw e;
    }
  }

  private void removeFromChangeList(@Nonnull Collection<Change> changes) {
    for (FilePath path : getPathsFromChanges(changes)) {
      try {
        File file = path.getIOFile();
        myVcs.getFactory(file).createChangeListClient().remove(file);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
  }

  private void addToChangeList(@Nonnull String changeList, @Nonnull Collection<Change> changes) {
    addToChangeList(changeList, changes, null);
  }

  private void addToChangeList(@Nonnull String changeList, @Nonnull Collection<Change> changes, @Nullable String[] changeListsToOperate) {
    for (FilePath path : getPathsFromChanges(changes)) {
      try {
        File file = path.getIOFile();
        myVcs.getFactory(file).createChangeListClient().add(changeList, file, changeListsToOperate);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
  }
}
