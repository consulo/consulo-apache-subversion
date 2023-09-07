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

import consulo.ide.impl.idea.openapi.vcs.history.BaseDiffFromHistoryHandler;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.diff.DirectoryWithBranchComparer;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnDiffFromHistoryHandler extends BaseDiffFromHistoryHandler<SvnFileRevision> {
  @Nonnull
  private final SvnVcs myVcs;

  public SvnDiffFromHistoryHandler(@Nonnull SvnVcs vcs) {
    super(vcs.getProject());
    myVcs = vcs;
  }

  @Nonnull
  @Override
  protected List<Change> getChangesBetweenRevisions(@Nonnull FilePath path, @Nonnull SvnFileRevision rev1, @Nullable SvnFileRevision rev2)
    throws VcsException {
    File file = path.getIOFile();
    SvnTarget target1 = SvnTarget.fromURL(SvnUtil.createUrl(rev1.getURL()), rev1.getRevision());
    SvnTarget target2 = rev2 != null ? SvnTarget.fromURL(SvnUtil.createUrl(rev2.getURL()), rev2.getRevision()) : SvnTarget.fromFile(file);

    return executeDiff(path, target1, target2);
  }

  @Nonnull
  @Override
  protected List<Change> getAffectedChanges(@Nonnull FilePath path,
                                            @Nonnull SvnFileRevision rev) throws VcsException {
    // Diff with zero revision is used here to get just affected changes under the path, and not all affected changes of the revision.
    SvnTarget target1 = SvnTarget.fromURL(SvnUtil.createUrl(rev.getURL()), SVNRevision.create(0));
    SvnTarget target2 = SvnTarget.fromURL(SvnUtil.createUrl(rev.getURL()), rev.getRevision());

    return executeDiff(path, target1, target2);
  }

  @Nonnull
  @Override
  protected String getPresentableName(@Nonnull SvnFileRevision revision) {
    return revision.getRevisionNumber().asString();
  }

  @Nonnull
  private List<Change> executeDiff(@Nonnull FilePath path,
                                   @Nonnull SvnTarget target1,
                                   @Nonnull SvnTarget target2) throws VcsException {
    File file = path.getIOFile();
    ClientFactory factory = target2.isURL() ? myVcs.getFactory(file) : DirectoryWithBranchComparer.getClientFactory(myVcs, file);

    return factory.createDiffClient().compare(target1, target2);
  }
}
