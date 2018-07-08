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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfo;

import static org.jetbrains.idea.svn.SvnUtil.ensureStartSlash;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.getRelativePath;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeContext {

  @Nonnull
  private final Project myProject;
  @Nonnull
  private final String myBranchName;
  @Nonnull
  private final VirtualFile myRoot;
  @Nonnull
  private final WCInfo myWcInfo;
  @Nonnull
  private final String mySourceUrl;
  @Nonnull
  private final SvnVcs myVcs;
  @Nonnull
  private final String myTitle;
  @Nonnull
  private final String myRepositoryRelativeSourcePath;
  @Nonnull
  private final String myRepositoryRelativeWorkingCopyPath;

  public MergeContext(@Nonnull SvnVcs vcs,
                      @Nonnull String sourceUrl,
                      @Nonnull WCInfo wcInfo,
                      @Nonnull String branchName,
                      @Nonnull VirtualFile root) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myBranchName = branchName;
    myRoot = root;
    mySourceUrl = sourceUrl;
    myWcInfo = wcInfo;
    myTitle = "Merge from " + myBranchName;
    myRepositoryRelativeSourcePath = ensureStartSlash(getRelativePath(myWcInfo.getRepositoryRoot(), mySourceUrl));
    myRepositoryRelativeWorkingCopyPath = ensureStartSlash(getRelativePath(myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl()));
  }

  @Nonnull
  public Project getProject() {
    return myProject;
  }

  @Nonnull
  public String getBranchName() {
    return myBranchName;
  }

  @Nonnull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @Nonnull
  public WCInfo getWcInfo() {
    return myWcInfo;
  }

  @Nonnull
  public String getSourceUrl() {
    return mySourceUrl;
  }

  @Nonnull
  public String getRepositoryRelativeSourcePath() {
    return myRepositoryRelativeSourcePath;
  }

  @Nonnull
  public String getRepositoryRelativeWorkingCopyPath() {
    return myRepositoryRelativeWorkingCopyPath;
  }

  @Nonnull
  public SvnVcs getVcs() {
    return myVcs;
  }

  @Nonnull
  public String getTitle() {
    return myTitle;
  }
}