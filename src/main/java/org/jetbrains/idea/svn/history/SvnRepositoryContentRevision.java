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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 17:48:18
 */
package org.jetbrains.idea.svn.history;

import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.ide.impl.idea.openapi.vcs.changes.ByteBackedContentRevision;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ContentRevisionCache;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static consulo.util.lang.ObjectUtil.notNull;

public class SvnRepositoryContentRevision extends SvnBaseContentRevision implements ByteBackedContentRevision {

  @Nonnull
  private final String myPath;
  private final long myRevision;

  public SvnRepositoryContentRevision(@Nonnull SvnVcs vcs, @Nonnull FilePath remotePath, @Nullable FilePath localPath, long revision) {
    super(vcs, notNull(localPath, remotePath));
    myPath = FileUtil.toSystemIndependentName(remotePath.getPath());
    myRevision = revision;
  }

  @Nonnull
  public String getContent() throws VcsException {
    return ContentRevisionCache.getAsString(getContentAsBytes(), myFile, null);
  }

  @Nonnull
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    try {
      if (myFile.getVirtualFile() == null) {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(myFile.getPath());
      }
      return ContentRevisionCache.getOrLoadAsBytes(myVcs.getProject(), myFile, getRevisionNumber(), myVcs.getKeyInstanceMethod(),
                                                   ContentRevisionCache.UniqueType.REPOSITORY_CONTENT, () -> loadContent().toByteArray());
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @Nonnull
  protected ByteArrayOutputStream loadContent() throws VcsException
  {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ContentLoader loader = new ContentLoader(myPath, buffer, myRevision);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(loader, SvnBundle.message("progress.title.loading.file.content"), false, null);
    }
    else {
      loader.run();
    }
    final Exception exception = loader.getException();
    if (exception != null) {
      throw new VcsException(exception);
    }
    ContentRevisionCache.checkContentsSize(myPath, buffer.size());
    return buffer;
  }

  @Nonnull
  public SvnRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(SVNRevision.create(myRevision));
  }

  public static SvnRepositoryContentRevision create(@Nonnull SvnVcs vcs,
                                                    @Nonnull String repositoryRoot,
                                                    @Nonnull String path,
                                                    @Nullable FilePath localPath,
                                                    long revision) {
    return create(vcs, SvnUtil.appendMultiParts(repositoryRoot, path), localPath, revision);
  }

  public static SvnRepositoryContentRevision createForRemotePath(@Nonnull SvnVcs vcs,
                                                                 @Nonnull String repositoryRoot,
                                                                 @Nonnull String path,
                                                                 boolean isDirectory,
                                                                 long revision) {
    FilePath remotePath = VcsUtil.getFilePathOnNonLocal(SvnUtil.appendMultiParts(repositoryRoot, path), isDirectory);
    return create(vcs, remotePath, remotePath, revision);
  }

  public static SvnRepositoryContentRevision create(@Nonnull SvnVcs vcs,
                                                    @Nonnull String fullPath,
                                                    @Nullable FilePath localPath,
                                                    long revision) {
    // TODO: Check if isDirectory = false always true for this method calls
    FilePath remotePath = VcsUtil.getFilePathOnNonLocal(fullPath, false);

    return create(vcs, remotePath, localPath == null ? remotePath : localPath, revision);
  }

  public static SvnRepositoryContentRevision create(@Nonnull SvnVcs vcs,
                                                    @Nonnull FilePath remotePath,
                                                    @Nullable FilePath localPath,
                                                    long revision) {
    return remotePath.getFileType().isBinary()
           ? new SvnRepositoryBinaryContentRevision(vcs, remotePath, localPath, revision)
           : new SvnRepositoryContentRevision(vcs, remotePath, localPath, revision);
  }

  @Override
  public String toString() {
    return myFile.getIOFile() + "#" + myRevision;
  }

  private class ContentLoader implements Runnable {
    private final String myPath;
    private final long myRevision;
    private final OutputStream myDst;
    private Exception myException;

    public ContentLoader(String path, OutputStream dst, long revision) {
      myPath = path;
      myDst = dst;
      myRevision = revision;
    }

    public Exception getException() {
      return myException;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.loading.contents", myPath));
        progress.setText2(SvnBundle.message("progress.text2.revision.information", myRevision));
      }

      try {
        // TODO: Local path could also be used here
        SVNRevision revision = SVNRevision.create(myRevision);
        byte[] contents = SvnUtil.getFileContents(myVcs, SvnTarget.fromURL(SvnUtil.parseUrl(getFullPath())), revision, revision);
        myDst.write(contents);
      }
      catch (VcsException | IOException e) {
        myException = e;
      }
    }
  }

  @Nonnull
  public String getFullPath() {
    return myPath;
  }

  public String getRelativePath(@Nonnull String repositoryUrl) {
    return SvnUtil.getRelativePath(repositoryUrl, myPath);
  }

  @Nonnull
  public SvnTarget toTarget() throws SvnBindException {
    return SvnTarget.fromURL(SvnUtil.createUrl(getFullPath()), getRevisionNumber().getRevision());
  }
}
