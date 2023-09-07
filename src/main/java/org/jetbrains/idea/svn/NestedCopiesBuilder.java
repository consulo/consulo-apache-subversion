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
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class NestedCopiesBuilder implements StatusReceiver {

  @Nonnull
  private final Set<NestedCopyInfo> myCopies;
  @Nonnull
  private final SvnFileUrlMapping myMapping;
  @Nonnull
  private final SvnVcs myVcs;

  public NestedCopiesBuilder(@Nonnull SvnVcs vcs, @Nonnull SvnFileUrlMapping mapping) {
    myVcs = vcs;
    myMapping = mapping;
    myCopies = new HashSet<>();
  }

  public void process(@Nonnull FilePath path, final Status status) {
    VirtualFile file = path.getVirtualFile();

    if (file != null) {
      if (status.is(StatusType.STATUS_EXTERNAL)) {
        // We do not determine here url, repository url - because url, repository url in status will determine location in the
        // repository where folder is located and not where svn:externals property points. We want the later parameters - they'll
        // determined while creating RootUrlInfos later. Format will be also determined later.
        // TODO: Probably we could move that logic here.
        myCopies.add(new NestedCopyInfo(file, null, WorkingCopyFormat.UNKNOWN, NestedCopyType.external, null));
      }
      else if (status.getURL() != null && !status.is(StatusType.STATUS_UNVERSIONED) && status.isSwitched()) {
        // this one called when there is switched directory under nested working copy
        // TODO: some other cases?
        myCopies.add(new NestedCopyInfo(file, status.getURL(), myVcs.getWorkingCopyFormat(path.getIOFile()), NestedCopyType.switched,
                                        status.getRepositoryRootURL()));
      }
    }
  }

  public void processIgnored(final VirtualFile vFile) {
  }

  public void processUnversioned(final VirtualFile vFile) {
  }

  @Override
  public void processCopyRoot(@Nonnull VirtualFile file,
                              @Nullable SVNURL url,
                              @Nonnull WorkingCopyFormat format,
                              @Nullable SVNURL rootURL) {
    myCopies.add(new NestedCopyInfo(file, url, format, NestedCopyType.inner, rootURL));
  }

  @Override
  public void bewareRoot(@Nonnull VirtualFile vf, SVNURL url) {
    final File ioFile = VfsUtilCore.virtualToIoFile(vf);
    final RootUrlInfo info = myMapping.getWcRootForFilePath(ioFile);

    if (info != null && FileUtil.filesEqual(ioFile, info.getIoFile()) && ! info.getAbsoluteUrlAsUrl().equals(url)) {
      myVcs.invokeRefreshSvnRoots();
    }
  }

  @Override
  public void finish() {
  }

  @Nonnull
  public Set<NestedCopyInfo> getCopies() {
    return myCopies;
  }
}
