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

import consulo.application.progress.ProgressIndicator;
import consulo.document.FileDocumentManager;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangelistBuilder;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.history.SimplePropertyRevision;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.svn.actions.ShowPropertiesDiffAction.getPropertyList;

class SvnChangeProviderContext implements StatusReceiver {
  private static final Logger LOG = Logger.getInstance(SvnChangeProviderContext.class);

  @Nonnull
  private final ChangelistBuilder myChangelistBuilder;
  @Nonnull
  private final List<SvnChangedFile> myCopiedFiles = ContainerUtil.newArrayList();
  @Nonnull
  private final List<SvnChangedFile> myDeletedFiles = ContainerUtil.newArrayList();
  // for files moved in a subtree, which were the targets of merge (for instance).
  @Nonnull
  private final Map<String, Status> myTreeConflicted = new HashMap<>();
  @Nonnull
  private final Map<FilePath, String> myCopyFromURLs = new HashMap<>();
  @Nonnull
  private final SvnVcs myVcs;
  private final SvnBranchConfigurationManager myBranchConfigurationManager;
  @Nonnull
  private final List<File> filesToRefresh = ContainerUtil.newArrayList();

  @Nullable
  private final ProgressIndicator myProgress;

  public SvnChangeProviderContext(@Nonnull SvnVcs vcs, @Nonnull ChangelistBuilder changelistBuilder, @Nullable ProgressIndicator progress) {
    myVcs = vcs;
    myChangelistBuilder = changelistBuilder;
    myProgress = progress;
    myBranchConfigurationManager = SvnBranchConfigurationManager.getInstance(myVcs.getProject());
  }

  public void process(FilePath path, Status status) throws SVNException {
    if (status != null) {
      processStatusFirstPass(path, status);
    }
  }

  public void processIgnored(VirtualFile vFile) {
    myChangelistBuilder.processIgnoredFile(vFile);
  }

  public void processUnversioned(VirtualFile vFile) {
    myChangelistBuilder.processUnversionedFile(vFile);
  }

  @Override
  public void processCopyRoot(VirtualFile file, SVNURL url, WorkingCopyFormat format, SVNURL rootURL) {
  }

  @Override
  public void bewareRoot(VirtualFile vf, SVNURL url) {
  }

  @Override
  public void finish() {
    LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh, true, false, null);
  }

  @Nonnull
  public ChangelistBuilder getBuilder() {
    return myChangelistBuilder;
  }

  public void reportTreeConflict(@Nonnull Status status) {
    myTreeConflicted.put(status.getFile().getAbsolutePath(), status);
  }

  @Nullable
  public Status getTreeConflictStatus(@Nonnull File file) {
    return myTreeConflicted.get(file.getAbsolutePath());
  }

  @Nonnull
  public List<SvnChangedFile> getCopiedFiles() {
    return myCopiedFiles;
  }

  @Nonnull
  public List<SvnChangedFile> getDeletedFiles() {
    return myDeletedFiles;
  }

  public boolean isDeleted(@Nonnull FilePath path) {
    for (SvnChangedFile deletedFile : myDeletedFiles) {
      if (Comparing.equal(path, deletedFile.getFilePath())) {
        return true;
      }
    }
    return false;
  }

  public void checkCanceled() {
    if (myProgress != null) {
      myProgress.checkCanceled();
    }
  }

  /**
   * If the specified filepath or its parent was added with history, returns the URL of the copy source for this filepath.
   *
   * @param filePath the original filepath
   * @return the copy source url, or null if the file isn't a copy of anything
   */
  @Nullable
  public String getParentCopyFromURL(@Nonnull FilePath filePath) {
    String result = null;
    FilePath parent = filePath;

    while (parent != null && !myCopyFromURLs.containsKey(parent)) {
      parent = parent.getParentPath();
    }

    if (parent != null) {
      String copyFromUrl = myCopyFromURLs.get(parent);

      //noinspection ConstantConditions
      result = parent == filePath
               ? copyFromUrl
               : SvnUtil.appendMultiParts(copyFromUrl, FileUtil.getRelativePath(parent.getIOFile(), filePath.getIOFile()));
    }

    return result;
  }

  public void addCopiedFile(@Nonnull FilePath filePath, @Nonnull Status status, @Nonnull String copyFromURL) {
    myCopiedFiles.add(new SvnChangedFile(filePath, status, copyFromURL));

    String value = status.getCopyFromURL();
    if (value != null) {
      myCopyFromURLs.put(filePath, value);
    }
  }

  void processStatusFirstPass(@Nonnull FilePath filePath, @Nonnull Status status) throws SVNException {
    if (status.getRemoteLock() != null) {
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(), status.getRemoteLock().toLogicalLock(false));
    }
    if (status.getLocalLock() != null) {
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(), status.getLocalLock().toLogicalLock(true));
    }
    if (filePath.isDirectory() && status.isLocked()) {
      myChangelistBuilder.processLockedFolder(filePath.getVirtualFile());
    }
    if ((status.is(StatusType.STATUS_ADDED) || StatusType.STATUS_MODIFIED.equals(status.getNodeStatus())) &&
        status.getCopyFromURL() != null) {
      addCopiedFile(filePath, status, status.getCopyFromURL());
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      myDeletedFiles.add(new SvnChangedFile(filePath, status));
    }
    else {
      String parentCopyFromURL = getParentCopyFromURL(filePath);
      if (parentCopyFromURL != null) {
        addCopiedFile(filePath, status, parentCopyFromURL);
      }
      else {
        processStatus(filePath, status);
      }
    }
  }

  void processStatus(@Nonnull FilePath filePath, @Nonnull Status status) throws SVNException {
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(filePath.getIOFile());
    if (!WorkingCopyFormat.UNKNOWN.equals(format) && format.less(WorkingCopyFormat.ONE_DOT_SEVEN)) {
      loadEntriesFile(filePath);
    }

    FileStatus fStatus = SvnStatusConvertor.convertStatus(status);

    final StatusType statusType = status.getContentsStatus();
    if (status.is(StatusType.STATUS_UNVERSIONED, StatusType.UNKNOWN)) {
      final VirtualFile file = filePath.getVirtualFile();
      if (file != null) {
        myChangelistBuilder.processUnversionedFile(file);
      }
    }
    else if (status.is(StatusType.STATUS_ADDED)) {
      processChangeInList(null, CurrentContentRevision.create(filePath), fStatus, status);
    }
    else if (status.is(StatusType.STATUS_CONFLICTED, StatusType.STATUS_MODIFIED, StatusType.STATUS_REPLACED) ||
             status.isProperty(StatusType.STATUS_MODIFIED, StatusType.STATUS_CONFLICTED)) {
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, status), CurrentContentRevision.create(filePath), fStatus,
                          status);
      checkSwitched(filePath, status, fStatus);
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, status), null, fStatus, status);
    }
    else if (status.is(StatusType.STATUS_MISSING)) {
      myChangelistBuilder.processLocallyDeletedFile(new SvnLocallyDeletedChange(filePath, getState(status)));
    }
    else if (status.is(StatusType.STATUS_IGNORED)) {
      VirtualFile file = filePath.getVirtualFile();
      if (file == null) {
        file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.getPath());
      }
      if (file == null) {
        LOG.error("No virtual file for ignored file: " + filePath.getPresentableUrl() + ", isNonLocal: " + filePath.isNonLocal());
      }
      else if (!myVcs.isWcRoot(filePath)) {
        myChangelistBuilder.processIgnoredFile(filePath.getVirtualFile());
      }
    }
    else if ((fStatus == FileStatus.NOT_CHANGED || fStatus == FileStatus.SWITCHED) && statusType != StatusType.STATUS_NONE) {
      VirtualFile file = filePath.getVirtualFile();
      if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
        processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, status), CurrentContentRevision.create(filePath),
                            FileStatus.MODIFIED, status);
      }
      else if (status.getTreeConflict() != null) {
        myChangelistBuilder.processChange(createChange(SvnContentRevision.createBaseRevision(myVcs, filePath, status),
                                                      CurrentContentRevision.create(filePath), FileStatus.MODIFIED, status),
                                          SvnVcs.getKey());
      }
      checkSwitched(filePath, status, fStatus);
    }
  }

  public void addModifiedNotSavedChange(@Nonnull VirtualFile file) throws SVNException {
    final FilePath filePath = VcsUtil.getFilePath(file);
    final Info svnInfo = myVcs.getInfo(file);

    if (svnInfo != null) {
      final Status svnStatus = new Status();
      svnStatus.setRevision(svnInfo.getRevision());
      svnStatus.setKind(NodeKind.from(filePath.isDirectory()));
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, svnInfo.getRevision()),
                          CurrentContentRevision.create(filePath), FileStatus.MODIFIED, svnStatus);
    }
  }

  private void processChangeInList(@Nullable ContentRevision beforeRevision,
                                   @Nullable ContentRevision afterRevision,
                                   @Nonnull FileStatus fileStatus,
                                   @Nonnull Status status) throws SVNException {
    Change change = createChange(beforeRevision, afterRevision, fileStatus, status);

    myChangelistBuilder.processChangeInList(change, SvnUtil.getChangelistName(status), SvnVcs.getKey());
  }

  private void checkSwitched(@Nonnull FilePath filePath, @Nonnull Status status, @Nonnull FileStatus convertedStatus) {
    if (status.isSwitched() || (convertedStatus == FileStatus.SWITCHED)) {
      final VirtualFile virtualFile = filePath.getVirtualFile();
      if (virtualFile == null) return;
      final String switchUrl = status.getURL().toString();
      final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(virtualFile);
      if (vcsRoot != null) {  // it will be null if we walked into an excluded directory
        String baseUrl = myBranchConfigurationManager.get(vcsRoot).getBaseName(switchUrl);
        myChangelistBuilder.processSwitchedFile(virtualFile, baseUrl == null ? switchUrl : baseUrl, true);
      }
    }
  }

  /**
   * Ensures that the contents of the 'entries' file is cached in the VFS, so that the VFS will send
   * correct events when the 'entries' file is changed externally (to be received by SvnEntriesFileListener)
   *
   * @param filePath the path of a changed file.
   */
  private void loadEntriesFile(@Nonnull FilePath filePath) {
    final FilePath parentPath = filePath.getParentPath();
    if (parentPath == null) {
      return;
    }
    refreshDotSvnAndEntries(parentPath);
    if (filePath.isDirectory()) {
      refreshDotSvnAndEntries(filePath);
    }
  }

  private void refreshDotSvnAndEntries(@Nonnull FilePath filePath) {
    final File svn = new File(filePath.getPath(), SvnUtil.SVN_ADMIN_DIR_NAME);

    filesToRefresh.add(svn);
    filesToRefresh.add(new File(svn, SvnUtil.ENTRIES_FILE_NAME));
  }

  // seems here we can only have a tree conflict; which can be marked on either path (?)
  // .. ok try to merge states
  @Nonnull
  Change createMovedChange(@Nonnull ContentRevision before,
                           @Nonnull ContentRevision after,
                           @Nullable Status copiedStatus,
                           @Nonnull Status deletedStatus) throws SVNException {
    // todo no convertion needed for the contents status?
    ConflictedSvnChange change =
      new ConflictedSvnChange(before, after, ConflictState.mergeState(getState(copiedStatus), getState(deletedStatus)),
                              ((copiedStatus != null) && (copiedStatus.getTreeConflict() != null)) ? after.getFile() : before.getFile());
    change.setBeforeDescription(deletedStatus.getTreeConflict());
    if (copiedStatus != null) {
      change.setAfterDescription(copiedStatus.getTreeConflict());
      patchWithPropertyChange(change, copiedStatus, deletedStatus);
    }

    return change;
  }

  @Nonnull
  private Change createChange(@Nullable ContentRevision before,
                              @Nullable ContentRevision after,
                              @Nonnull FileStatus fStatus,
                              @Nonnull Status svnStatus)
    throws SVNException {
    ConflictedSvnChange change =
      new ConflictedSvnChange(before, after, fStatus, getState(svnStatus), after == null ? before.getFile() : after.getFile());

    change.setIsPhantom(StatusType.STATUS_DELETED.equals(svnStatus.getNodeStatus()) && !svnStatus.getRevision().isValid());
    change.setBeforeDescription(svnStatus.getTreeConflict());
    patchWithPropertyChange(change, svnStatus, null);

    return change;
  }

  private void patchWithPropertyChange(@Nonnull Change change, @Nonnull Status svnStatus, @Nullable Status deletedStatus)
    throws SVNException {
    if (svnStatus.isProperty(StatusType.STATUS_CONFLICTED, StatusType.CHANGED, StatusType.STATUS_ADDED, StatusType.STATUS_DELETED,
                             StatusType.STATUS_MODIFIED, StatusType.STATUS_REPLACED, StatusType.MERGED)) {
      change.addAdditionalLayerElement(SvnChangeProvider.PROPERTY_LAYER, createPropertyChange(change, svnStatus, deletedStatus));
    }
  }

  @Nonnull
  private Change createPropertyChange(@Nonnull Change change, @Nonnull Status svnStatus, @Nullable Status deletedStatus)
    throws SVNException {
    final File ioFile = ChangesUtil.getFilePath(change).getIOFile();
    final File beforeFile = deletedStatus != null ? deletedStatus.getFile() : ioFile;

    // TODO: There are cases when status output is like (on newly added file with some properties that is locally deleted)
    // <entry path="some_path"> <wc-status item="missing" revision="-1" props="modified"> </wc-status> </entry>
    // TODO: For such cases in current logic we'll have Change with before revision containing SVNRevision.UNDEFINED
    // TODO: Analyze if this logic is OK or we should update flow somehow (for instance, to have null before revision)
    ContentRevision beforeRevision =
      !svnStatus.isProperty(StatusType.STATUS_ADDED) || deletedStatus != null ? createPropertyRevision(change, beforeFile, true) : null;
    ContentRevision afterRevision = !svnStatus.isProperty(StatusType.STATUS_DELETED) ? createPropertyRevision(change, ioFile, false) : null;
    FileStatus status =
      deletedStatus != null ? FileStatus.MODIFIED : SvnStatusConvertor.convertPropertyStatus(svnStatus.getPropertiesStatus());

    return new Change(beforeRevision, afterRevision, status);
  }

  @Nullable
  private ContentRevision createPropertyRevision(@Nonnull Change change, @Nonnull File file, boolean isBeforeRevision)
    throws SVNException {
    FilePath path = ChangesUtil.getFilePath(change);
    ContentRevision contentRevision = isBeforeRevision ? change.getBeforeRevision() : change.getAfterRevision();
    SVNRevision revision = isBeforeRevision ? SVNRevision.BASE : SVNRevision.WORKING;

    return new SimplePropertyRevision(getPropertyList(myVcs, file, revision), path, getRevisionNumber(contentRevision));
  }

  @Nullable
  private static String getRevisionNumber(@Nullable ContentRevision revision) {
    return revision != null ? revision.getRevisionNumber().asString() : null;
  }

  @Nonnull
  private ConflictState getState(@Nullable Status svnStatus) {
    ConflictState result = svnStatus != null ? ConflictState.from(svnStatus) : ConflictState.none;

    if (result.isTree()) {
      //noinspection ConstantConditions
      reportTreeConflict(svnStatus);
    }

    return result;
  }
}
