/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.util.io.UriUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class RootUrlInfo implements RootUrlPair {

  @Nonnull
  private final String myRepositoryUrl;
  @Nonnull
  private final WorkingCopyFormat myFormat;
  @Nonnull
  private final Node myNode;
  // vcs root
  @Nonnull
  private final VirtualFile myRoot;
  @Nullable
  private volatile NestedCopyType myType;

  public RootUrlInfo(@Nonnull final Node node, @Nonnull final WorkingCopyFormat format, @Nonnull final VirtualFile root) {
    this(node, format, root, null);
  }

  public RootUrlInfo(@Nonnull final Node node,
                     @Nonnull final WorkingCopyFormat format,
                     @Nonnull final VirtualFile root,
                     @Nullable final NestedCopyType type) {
    myNode = node;
    myFormat = format;
    myRoot = root;
    myRepositoryUrl = UriUtil.trimTrailingSlashes(node.getRepositoryRootUrl().toString());
    myType = type;
  }

  @Nonnull
  public Node getNode() {
    return myNode;
  }

  @Nonnull
  public String getRepositoryUrl() {
    return myRepositoryUrl;
  }

  @Nonnull
  public SVNURL getRepositoryUrlUrl() {
    return myNode.getRepositoryRootUrl();
  }

  @Nonnull
  public String getAbsoluteUrl() {
    return getAbsoluteUrlAsUrl().toString();
  }

  @Nonnull
  public SVNURL getAbsoluteUrlAsUrl() {
    return myNode.getUrl();
  }

  @Nonnull
  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  @Nonnull
  public File getIoFile() {
    return myNode.getIoFile();
  }

  @Nonnull
  public String getPath() {
    return getIoFile().getAbsolutePath();
  }

  // vcs root
  @Nonnull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @Nonnull
  public VirtualFile getVirtualFile() {
    return myNode.getFile();
  }

  @Nonnull
  public String getUrl() {
    return getAbsoluteUrl();
  }

  @Nullable
  public NestedCopyType getType() {
    return myType;
  }

  public void setType(@Nullable NestedCopyType type) {
    myType = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RootUrlInfo info = (RootUrlInfo)o;

    if (myFormat != info.myFormat) return false;
    if (!myNode.equals(info.myNode)) return false;
    if (!myRoot.equals(info.myRoot)) return false;
    if (myType != info.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFormat.hashCode();
    result = 31 * result + myNode.hashCode();
    result = 31 * result + myRoot.hashCode();
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
