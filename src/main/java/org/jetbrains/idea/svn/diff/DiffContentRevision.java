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
package org.jetbrains.idea.svn.diff;

import consulo.ide.impl.idea.openapi.vcs.changes.ByteBackedContentRevision;
import consulo.util.io.BufferExposingByteArrayOutputStream;
import consulo.util.io.CharsetToolkit;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.util.VcsUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.annotation.Nonnull;
import java.nio.charset.Charset;

public class DiffContentRevision implements ByteBackedContentRevision {
  private String myPath;
  private SVNRepository myRepository;
  private byte[] myContents;
  private FilePath myFilePath;
  private long myRevision;

  public DiffContentRevision(String path, @Nonnull SVNRepository repos, long revision) {
    this(path, repos, revision, VcsUtil.getFilePath(path));
  }

  public DiffContentRevision(final String path, final SVNRepository repository, final long revision, final FilePath filePath) {
    myPath = path;
    myRepository = repository;
    myFilePath = filePath;
    myRevision = revision;
  }

  @Nonnull
  public String getContent() throws VcsException
  {
    final byte[] bytes = getContentAsBytes();
    final Charset charset = myFilePath.getCharset();
    return CharsetToolkit.bytesToString(bytes, charset);
  }

  @Nonnull
  @Override
  public byte[] getContentAsBytes() throws VcsException
  {
    if (myContents == null) {
      BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream(2048);
      try {
        myRepository.getFile(myPath, -1, null, bos);
        myRepository.closeSession();
      }
      catch (SVNException e) {
        throw new VcsException(e);
      }
      myContents = bos.toByteArray();
    }
    return myContents;
  }

  @Nonnull
  public FilePath getFile() {
    return myFilePath;
  }

  @Nonnull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber.Long(myRevision);
  }
}
