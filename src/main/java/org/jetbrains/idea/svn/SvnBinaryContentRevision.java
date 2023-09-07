/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import javax.annotation.Nonnull;

import consulo.versionControlSystem.change.BinaryContentRevision;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author yole
*/
class SvnBinaryContentRevision extends SvnContentRevision implements BinaryContentRevision {

  public SvnBinaryContentRevision(@Nonnull SvnVcs vcs, @Nonnull FilePath file, @Nonnull SVNRevision revision, boolean useBaseRevision) {
    super(vcs, file, revision, useBaseRevision);
  }

  @Nullable
  public byte[] getBinaryContent() throws VcsException
  {
    return getContentAsBytes();
  }

  @NonNls
  public String toString() {
    return "SvnBinaryContentRevision:" + myFile;
  }
}
