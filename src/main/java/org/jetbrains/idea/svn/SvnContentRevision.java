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

import consulo.ide.impl.idea.openapi.vcs.changes.ByteBackedContentRevision;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ContentRevisionCache;
import consulo.versionControlSystem.change.CurrentRevisionProvider;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.status.Status;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class SvnContentRevision extends SvnBaseContentRevision implements ByteBackedContentRevision {

  @Nonnull
  private final SVNRevision myRevision;
  /**
   * this flag is necessary since SVN would not do remote request only if constant SVNRevision.BASE
   * -> usual current revision content class can't be used
   */
  private final boolean myUseBaseRevision;

  protected SvnContentRevision(@Nonnull SvnVcs vcs,
                               @Nonnull FilePath file,
                               @Nonnull SVNRevision revision,
                               boolean useBaseRevision) {
    super(vcs, file);
    myRevision = revision;
    myUseBaseRevision = useBaseRevision;
  }

  @Nonnull
  public static SvnContentRevision createBaseRevision(@Nonnull SvnVcs vcs, @Nonnull FilePath file, @Nonnull Status status) {
    SVNRevision revision = status.getRevision().isValid() ? status.getRevision() : status.getCommittedRevision();
    return createBaseRevision(vcs, file, revision);
  }

  @Nonnull
  public static SvnContentRevision createBaseRevision(@Nonnull SvnVcs vcs,
                                                      @Nonnull FilePath file,
                                                      @Nonnull SVNRevision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, true);
    }
    return new SvnContentRevision(vcs, file, revision, true);
  }

  @Nonnull
  public static SvnContentRevision createRemote(@Nonnull SvnVcs vcs,
                                                @Nonnull FilePath file,
                                                @Nonnull SVNRevision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, false);
    }
    return new SvnContentRevision(vcs, file, revision, false);
  }

  @Nullable
  public String getContent() throws VcsException {
    return ContentRevisionCache.getAsString(getContentAsBytes(), myFile, null);
  }

  @Nullable
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    try {
      if (myUseBaseRevision) {
        return ContentRevisionCache.getOrLoadCurrentAsBytes(myVcs.getProject(), myFile, myVcs.getKeyInstanceMethod(),
                                                            new CurrentRevisionProvider() {
                                                              @Override
                                                              public VcsRevisionNumber getCurrentRevision() throws VcsException {
                                                                return getRevisionNumber();
                                                              }

                                                              @Override
                                                              public Pair<VcsRevisionNumber, byte[]> get()
                                                                throws VcsException, IOException {
                                                                return Pair.create(getRevisionNumber(), getUpToDateBinaryContent());
                                                              }
                                                            }).getSecond();
      }
      else {
        return ContentRevisionCache.getOrLoadAsBytes(myVcs.getProject(),
                                                     myFile,
                                                     getRevisionNumber(),
                                                     myVcs.getKeyInstanceMethod(),
                                                     ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                                                     () -> getUpToDateBinaryContent());
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private byte[] getUpToDateBinaryContent() throws VcsException {
    File file = myFile.getIOFile();
    File lock = new File(file.getParentFile(), SvnUtil.PATH_TO_LOCK_FILE);
    if (lock.exists()) {
      throw new VcsException("Can not access file base revision contents: administrative area is locked");
    }
    return SvnUtil.getFileContents(myVcs, SvnTarget.fromFile(file), myUseBaseRevision ? SVNRevision.BASE : myRevision,
                                   SVNRevision.UNDEFINED);
  }

  @Nonnull
  public VcsRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(myRevision);
  }

  @NonNls
  public String toString() {
    return myFile.getPath();
  }
}
