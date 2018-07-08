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
package org.jetbrains.idea.svn.dialogs;

import javax.annotation.Nonnull;

import com.intellij.openapi.vfs.VirtualFile;

import javax.annotation.Nullable;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

public class WCInfo {

  private final boolean myIsWcRoot;
  @Nonnull
  private final Depth myStickyDepth;
  @Nonnull
  private final RootUrlInfo myRootInfo;

  public WCInfo(@Nonnull RootUrlInfo rootInfo, boolean isWcRoot, @Nonnull Depth stickyDepth) {
    myRootInfo = rootInfo;
    myIsWcRoot = isWcRoot;
    myStickyDepth = stickyDepth;
  }

  @Nonnull
  public Depth getStickyDepth() {
    return myStickyDepth;
  }

  @Nonnull
  public String getPath() {
    return myRootInfo.getPath();
  }

  @Nullable
  public VirtualFile getVcsRoot() {
    return null;
  }

  @Nonnull
  public SVNURL getUrl() {
    return myRootInfo.getAbsoluteUrlAsUrl();
  }

  @Nonnull
  public String getRootUrl() {
    return getUrl().toString();
  }

  @Nonnull
  public String getRepoUrl() {
    return getRepositoryRoot();
  }

  @Nonnull
  public RootUrlInfo getRootInfo() {
    return myRootInfo;
  }

  public boolean hasError() {
    return getRootInfo().getNode().hasError();
  }

  @Nonnull
  public String getErrorMessage() {
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    SvnBindException error = getRootInfo().getNode().getError();

    return error != null ? error.getMessage() : "";
  }

  @Nonnull
  public WorkingCopyFormat getFormat() {
    return myRootInfo.getFormat();
  }

  @Nonnull
  public String getRepositoryRoot() {
    return myRootInfo.getRepositoryUrl();
  }

  public boolean isIsWcRoot() {
    return myIsWcRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WCInfo)) return false;

    final WCInfo wcInfo = (WCInfo)o;

    return getPath().equals(wcInfo.getPath());
  }

  @Override
  public int hashCode() {
    return getPath().hashCode();
  }

  @Nullable
  public NestedCopyType getType() {
    return myRootInfo.getType();
  }
}
