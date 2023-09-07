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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.*;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsConfiguration;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.history.VcsAppendableHistorySessionPartner;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.history.VcsHistoryProvider;
import consulo.application.util.Semaphore;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.history.VcsAbstractHistorySession;
import junit.framework.Assert;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/27/12
 * Time: 5:15 PM
 */
public abstract class SvnHistoryTest extends Svn17TestCase {
  private volatile int myCnt;

  @Test
  public void testRepositoryRootHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final FilePath rootPath = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(myRepoUrl, true);
    provider.reportAppendableHistory(rootPath, new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
        semaphore.up();
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertTrue(myCnt > 0);
  }

  @Test
  public void testSimpleHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final FilePath rootPath = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(myRepoUrl + "/root/source/s1.txt", true);
    provider.reportAppendableHistory(rootPath, new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertEquals(11, myCnt);
  }

  @Test
  public void testSimpleHistoryLocal() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    provider.reportAppendableHistory(VcsUtil.getFilePath(tree.myS1File), new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertEquals(11, myCnt);
  }

  @Test
  public void testLocallyRenamedFileHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    VcsTestUtil.renameFileInCommand(myProject, tree.myS1File, "renamed.txt");
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager.getInstance(myProject).ensureUpToDate(false);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    provider.reportAppendableHistory(VcsUtil.getFilePath(tree.myS1File), new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertEquals(11, myCnt);
  }

  @Test
  public void testLocallyMovedToRenamedDirectory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    VcsTestUtil.renameFileInCommand(myProject, tree.myTargetDir, "renamedTarget");
    VcsTestUtil.moveFileInCommand(myProject, tree.myS1File, tree.myTargetDir);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager.getInstance(myProject).ensureUpToDate(false);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    provider.reportAppendableHistory(VcsUtil.getFilePath(tree.myS1File), new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertEquals(11, myCnt);

    myCnt = 0;
    semaphore.down();
    provider.reportAppendableHistory(VcsUtil.getFilePath(tree.myTargetDir), new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);
    Assert.assertEquals(1, myCnt);

    myCnt = 0;
    semaphore.down();
    provider.reportAppendableHistory(VcsUtil.getFilePath(tree.myTargetFiles.get(0)), new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);
    Assert.assertEquals(1, myCnt);
  }
}
