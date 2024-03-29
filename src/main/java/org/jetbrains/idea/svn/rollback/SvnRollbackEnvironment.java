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
package org.jetbrains.idea.svn.rollback;

import consulo.ide.impl.idea.openapi.vcs.rollback.DefaultRollbackEnvironment;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.rollback.RollbackProgressListener;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class SvnRollbackEnvironment extends DefaultRollbackEnvironment {
  private final SvnVcs mySvnVcs;

  public SvnRollbackEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  @Override
  public String getRollbackOperationName() {
    return SvnBundle.message("action.name.revert");
  }

  public void rollbackChanges(@Nonnull List<Change> changes,
                              @Nonnull List<VcsException> exceptions,
                              @Nonnull RollbackProgressListener listener) {
    listener.indeterminate();

    for (Map.Entry<Pair<SVNURL, WorkingCopyFormat>, Collection<Change>> entry : SvnUtil.splitChangesIntoWc(mySvnVcs, changes).entrySet()) {
      // to be more sure about nested changes, being or being not reverted
      List<Change> sortedChanges = ContainerUtil.sorted(entry.getValue(), ChangesAfterPathComparator.getInstance());

      rollbackGroupForWc(sortedChanges, exceptions, listener);
    }
  }

  private void rollbackGroupForWc(@Nonnull List<Change> changes,
                                  @Nonnull List<VcsException> exceptions,
                                  @Nonnull RollbackProgressListener listener) {
    final UnversionedAndNotTouchedFilesGroupCollector collector = new UnversionedAndNotTouchedFilesGroupCollector();
    final ChangesChecker checker = new ChangesChecker(mySvnVcs, collector);

    checker.gather(changes);
    exceptions.addAll(checker.getExceptions());

    final Reverter reverter = new Reverter(mySvnVcs, listener, exceptions);
    reverter.moveRenamesToTmp(collector);
    reverter.revert(checker.getForAdds(), true);
    reverter.revert(checker.getForDeletes(), true);
    reverter.revert(checker.getForEdits(), false);
    reverter.moveGroup();

    for (Couple<File> pair : collector.getToBeDeleted()) {
      if (pair.getFirst().exists()) {
        FileUtil.delete(pair.getSecond());
      }
    }
  }

  public void rollbackMissingFileDeletion(@Nonnull List<FilePath> filePaths,
                                          @Nonnull List<VcsException> exceptions,
                                          @Nonnull RollbackProgressListener listener) {
    for (FilePath filePath : filePaths) {
      listener.accept(filePath);
      try {
        revertFileOrDir(filePath);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
      catch (SVNException e) {
        exceptions.add(new VcsException(e));
      }
    }
  }

  private void revertFileOrDir(@Nonnull FilePath filePath) throws SVNException, VcsException {
    File file = filePath.getIOFile();
    Info info = mySvnVcs.getInfo(file);
    if (info != null) {
      if (info.isFile()) {
        doRevert(file, false);
      }
      else if (Info.SCHEDULE_ADD.equals(info.getSchedule()) || is17OrGreaterCopy(file, info)) {
        doRevert(file, true);
      }
      else {
        // do update to restore missing directory.
        mySvnVcs.getSvnKitManager().createUpdateClient().doUpdate(file, SVNRevision.HEAD, true);
      }
    }
    else {
      throw new VcsException("Can not get 'svn info' for " + file.getPath());
    }
  }

  private void doRevert(@Nonnull File path, boolean recursive) throws VcsException {
    mySvnVcs.getFactory(path).createRevertClient().revert(Collections.singletonList(path), Depth.allOrFiles(recursive), null);
  }

  private boolean is17OrGreaterCopy(@Nonnull File file, @Nonnull Info info) throws VcsException {
    WorkingCopy copy = mySvnVcs.getRootsToWorkingCopies().getMatchingCopy(info.getURL());

    return copy != null ? copy.is17Copy() : mySvnVcs.getWorkingCopyFormat(file).isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN);
  }

  public static boolean isMoveRenameReplace(@Nonnull Change c) {
    if (c.getAfterRevision() == null || c.getBeforeRevision() == null) return false;

    return c.isIsReplaced() ||
           c.isMoved() ||
           c.isRenamed() ||
           (!Comparing.equal(c.getBeforeRevision().getFile(), c.getAfterRevision().getFile()));
  }
}
