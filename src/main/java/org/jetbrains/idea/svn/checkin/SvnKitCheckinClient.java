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
package org.jetbrains.idea.svn.checkin;

import consulo.application.progress.ProgressManager;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.VcsException;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCheckinClient extends BaseSvnClient implements CheckinClient {

  private static final Logger LOG = Logger.getInstance(SvnKitCheckinClient.class);

  @Nonnull
  @Override
  public CommitInfo[] commit(@Nonnull List<File> paths, @Nonnull String comment) throws VcsException
  {
    File[] pathsToCommit = ArrayUtil.toObjectArray(paths, File.class);
    boolean keepLocks = myVcs.getSvnConfiguration().isKeepLocks();
    SVNCommitPacket[] commitPackets = null;
    SVNCommitInfo[] results;
    SVNCommitClient committer = myVcs.getSvnKitManager().createCommitClient();
    IdeaCommitHandler handler = new IdeaCommitHandler(ProgressManager.getInstance().getProgressIndicator(), true, true);

    committer.setEventHandler(toEventHandler(handler));
    try {
      commitPackets = committer.doCollectCommitItems(pathsToCommit, keepLocks, true, SVNDepth.EMPTY, true, null);
      results = committer.doCommit(commitPackets, keepLocks, comment);
      commitPackets = null;
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
    finally {
      if (commitPackets != null) {
        for (SVNCommitPacket commitPacket : commitPackets) {
          try {
            commitPacket.dispose();
          }
          catch (SVNException e) {
            LOG.info(e);
          }
        }
      }
    }

    // This seems to be necessary only for SVNKit as changes after command line operations should be detected during VFS refresh.
    for (VirtualFile f : handler.getDeletedFiles()) {
      f.putUserData(VirtualFile.REQUESTOR_MARKER, this);
    }

    return convert(results);
  }

  @Nonnull
  private static CommitInfo[] convert(@Nonnull SVNCommitInfo[] infos) {
    return ContainerUtil.map(infos, info -> new CommitInfo.Builder(info.getNewRevision(), info.getDate(), info.getAuthor())
      .setError(info.getErrorMessage()).build(), new CommitInfo[0]);
  }
}
