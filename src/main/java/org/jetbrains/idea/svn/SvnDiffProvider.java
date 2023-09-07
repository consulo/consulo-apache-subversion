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

import consulo.ide.impl.idea.openapi.vcs.history.VcsRevisionDescriptionImpl;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.diff.DiffProvider;
import consulo.versionControlSystem.diff.DiffProviderEx;
import consulo.versionControlSystem.diff.ItemLatestState;
import consulo.versionControlSystem.history.VcsRevisionDescription;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.LatestExistentSearcher;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.info.InfoConsumer;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SvnDiffProvider extends DiffProviderEx implements DiffProvider {

  private static final Logger LOG = Logger.getInstance(SvnDiffProvider.class);

  public static final String COMMIT_MESSAGE = "svn:log";
  private static final int BATCH_INFO_SIZE = 20;

  @Nonnull
  private final SvnVcs myVcs;

  public SvnDiffProvider(@Nonnull SvnVcs vcs) {
    myVcs = vcs;
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision(@Nonnull VirtualFile file) {
    final Info svnInfo = myVcs.getInfo(VfsUtilCore.virtualToIoFile(file));

    return getRevision(svnInfo);
  }

  @Nullable
  private static VcsRevisionNumber getRevision(@Nullable Info info) {
    VcsRevisionNumber result = null;

    if (info != null) {
      SVNRevision revision = SVNRevision.UNDEFINED.equals(info.getCommittedRevision()) && info.getCopyFromRevision() != null
                             ? info.getCopyFromRevision()
                             : info.getRevision();

      result = new SvnRevisionNumber(revision);
    }

    return result;
  }

  @Nonnull
  @Override
  public Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(@Nonnull Iterable<VirtualFile> files) {
    Map<VirtualFile, VcsRevisionNumber> result = new HashMap<>();
    Map<String, VirtualFile> items = new HashMap<>();
    List<File> ioFiles = ContainerUtil.newArrayList();

    for (VirtualFile file : files) {
      File ioFile = VirtualFileUtil.virtualToIoFile(file);
      ioFiles.add(ioFile);
      items.put(ioFile.getAbsolutePath(), file);

      // process in blocks of BATCH_INFO_SIZE size
      if (items.size() == BATCH_INFO_SIZE) {
        collectRevisionsInBatch(result, items, ioFiles);
        items.clear();
        ioFiles.clear();
      }
    }
    // process left files
    collectRevisionsInBatch(result, items, ioFiles);

    return result;
  }

  private void collectRevisionsInBatch(@Nonnull Map<VirtualFile, VcsRevisionNumber> revisionMap,
                                       @Nonnull Map<String, VirtualFile> fileMap,
                                       @Nonnull List<File> ioFiles) {
    myVcs.collectInfo(ioFiles, createInfoHandler(revisionMap, fileMap));
  }

  @Nonnull
  private static InfoConsumer createInfoHandler(@Nonnull final Map<VirtualFile, VcsRevisionNumber> revisionMap,
                                                @Nonnull final Map<String, VirtualFile> fileMap) {
    return new InfoConsumer() {
      @Override
      public void consume(Info info) throws SVNException {
        if (info != null) {
          VirtualFile file = fileMap.get(info.getFile().getAbsolutePath());

          if (file != null) {
            revisionMap.put(file, getRevision(info));
          }
          else {
            LOG.info("Could not find virtual file for path " + info.getFile().getAbsolutePath());
          }
        }
      }
    };
  }

  @Nullable
  @Override
  public VcsRevisionDescription getCurrentRevisionDescription(@Nonnull VirtualFile file) {
    return getCurrentRevisionDescription(VirtualFileUtil.virtualToIoFile(file));
  }

  @Nullable
  private VcsRevisionDescription getCurrentRevisionDescription(@Nonnull File path) {
    final Info svnInfo = myVcs.getInfo(path);
    if (svnInfo == null) {
      return null;
    }

    if (svnInfo.getCommittedRevision().equals(SVNRevision.UNDEFINED) &&
        !svnInfo.getCopyFromRevision().equals(SVNRevision.UNDEFINED) &&
        svnInfo.getCopyFromURL() != null) {
      File localPath = myVcs.getSvnFileUrlMapping().getLocalPath(svnInfo.getCopyFromURL().toString());

      if (localPath != null) {
        return getCurrentRevisionDescription(localPath);
      }
    }

    return new VcsRevisionDescriptionImpl(new SvnRevisionNumber(svnInfo.getCommittedRevision()), svnInfo.getCommittedDate(),
                                          svnInfo.getAuthor(), getCommitMessage(path, svnInfo));
  }

  @Nullable
  private String getCommitMessage(@Nonnull File path, @Nonnull Info info) {
    String result;

    try {
      PropertyValue property =
        myVcs.getFactory(path).createPropertyClient()
          .getProperty(SvnTarget.fromFile(path), COMMIT_MESSAGE, true, info.getCommittedRevision());

      result = PropertyValue.toString(property);
    }
    catch (VcsException e) {
      LOG.info("Failed to get commit message for file " + path + ", " + info.getCommittedRevision() + ", " + info.getRevision(), e);
      result = "";
    }

    return result;
  }

  @Nonnull
  private static ItemLatestState defaultResult() {
    return createResult(SVNRevision.HEAD, true, true);
  }

  @Nonnull
  private static ItemLatestState createResult(@Nonnull SVNRevision revision, boolean exists, boolean defaultHead) {
    return new ItemLatestState(new SvnRevisionNumber(revision), exists, defaultHead);
  }

  @Nonnull
  @Override
  public ItemLatestState getLastRevision(@Nonnull VirtualFile file) {
    return getLastRevision(VirtualFileUtil.virtualToIoFile(file));
  }

  @Nonnull
  @Override
  public ContentRevision createFileContent(@Nonnull VcsRevisionNumber revisionNumber, @Nonnull VirtualFile selectedFile) {
    FilePath filePath = VcsUtil.getFilePath(selectedFile);
    SVNRevision svnRevision = ((SvnRevisionNumber)revisionNumber).getRevision();

    if (!SVNRevision.HEAD.equals(svnRevision) && revisionNumber.equals(getCurrentRevision(selectedFile))) {
      return SvnContentRevision.createBaseRevision(myVcs, filePath, svnRevision);
    }

    // not clear why we need it, with remote check..
    Status svnStatus = getFileStatus(VirtualFileUtil.virtualToIoFile(selectedFile), false);

    return svnStatus != null && svnRevision.equals(svnStatus.getRevision())
           ? SvnContentRevision.createBaseRevision(myVcs, filePath, svnRevision)
           : SvnContentRevision.createRemote(myVcs, filePath, svnRevision);
  }

  @Nullable
  private Status getFileStatus(@Nonnull File file, boolean remote) {
    Status result = null;

    try {
      result = myVcs.getFactory(file).createStatusClient().doStatus(file, remote);
    }
    catch (SvnBindException e) {
      LOG.debug(e);
    }

    return result;
  }

  @Nonnull
  @Override
  public ItemLatestState getLastRevision(@Nonnull FilePath filePath) {
    return getLastRevision(filePath.getIOFile());
  }

  @Nullable
  @Override
  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }

  @Nonnull
  private ItemLatestState getLastRevision(@Nonnull File file) {
    Status svnStatus = getFileStatus(file, true);

    if (svnStatus == null || itemExists(svnStatus) && SVNRevision.UNDEFINED.equals(svnStatus.getRemoteRevision())) {
      // IDEADEV-21785 (no idea why this can happen)
      final Info info = myVcs.getInfo(file, SVNRevision.HEAD);
      if (info == null || info.getURL() == null) {
        LOG.info("No SVN status returned for " + file.getPath());
        return defaultResult();
      }
      return createResult(info.getCommittedRevision(), true, false);
    }

    if (!itemExists(svnStatus)) {
      return createResult(getLastExistingRevision(file, svnStatus), false, false);
    }
    return createResult(ObjectUtil.notNull(svnStatus.getRemoteRevision(), svnStatus.getRevision()), true, false);
  }

  @Nonnull
  private SVNRevision getLastExistingRevision(@Nonnull File file, @Nonnull Status svnStatus) {
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(file);
    long revision = -1;

    // skipped for >= 1.8
    if (format.less(WorkingCopyFormat.ONE_DOT_EIGHT)) {
      // get really latest revision
      // TODO: Algorithm seems not to be correct in all cases - for instance, when some subtree was deleted and replaced by other
      // TODO: with same names. pegRevision should be used somehow but this complicates the algorithm
      if (svnStatus.getRepositoryRootURL() != null) {
        revision = new LatestExistentSearcher(myVcs, svnStatus.getURL(), svnStatus.getRepositoryRootURL()).getDeletionRevision();
      }
      else {
        LOG.info("Could not find repository url for file " + file);
      }
    }

    return SVNRevision.create(revision);
  }

  private static boolean itemExists(@Nonnull Status svnStatus) {
    return !StatusType.STATUS_DELETED.equals(svnStatus.getRemoteContentsStatus()) &&
           !StatusType.STATUS_DELETED.equals(svnStatus.getRemoteNodeStatus());
  }
}
