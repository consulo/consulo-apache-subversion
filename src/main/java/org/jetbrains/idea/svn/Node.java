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

import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

// TODO: Probably we could utilize VcsVirtualFile instead of this class. VcsVirtualVile contains VcsFileRevision that
// TODO: provides RepositoryLocation
public class Node {

  @Nonnull
  private final VirtualFile myFile;
  @Nonnull
  private final SVNURL myUrl;
  @Nonnull
  private final SVNURL myRepositoryUrl;
  @Nullable private final SvnBindException myError;

  public Node(@Nonnull VirtualFile file, @Nonnull SVNURL url, @Nonnull SVNURL repositoryUrl) {
    this(file, url, repositoryUrl, null);
  }

  public Node(@Nonnull VirtualFile file, @Nonnull SVNURL url, @Nonnull SVNURL repositoryUrl, @Nullable SvnBindException error) {
    myFile = file;
    myUrl = url;
    myRepositoryUrl = repositoryUrl;
    myError = error;
  }

  @Nonnull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nonnull
  public File getIoFile() {
    return VfsUtilCore.virtualToIoFile(getFile());
  }

  @Nonnull
  public SVNURL getUrl() {
    return myUrl;
  }

  @Nonnull
  public SVNURL getRepositoryRootUrl() {
    return myRepositoryUrl;
  }

  @Nullable
  public SvnBindException getError() {
    return myError;
  }

  public boolean hasError() {
    return myError != null;
  }

  public boolean onUrl(@Nullable SVNURL url) {
    return myUrl.equals(url);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Node node = (Node)o;

    if (myError != null ? (node.myError == null || !myError.getMessage().equals(node.myError.getMessage())) : node.myError != null) {
      return false;
    }
    if (!myFile.equals(node.myFile)) return false;
    if (!myRepositoryUrl.equals(node.myRepositoryUrl)) return false;
    if (!myUrl.equals(node.myUrl)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myUrl.hashCode();
    result = 31 * result + myRepositoryUrl.hashCode();
    result = 31 * result + (myError != null ? myError.getMessage().hashCode() : 0);
    return result;
  }
}
