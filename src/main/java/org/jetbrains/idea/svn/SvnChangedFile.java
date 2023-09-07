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

import consulo.util.lang.ObjectUtil;
import consulo.versionControlSystem.FilePath;
import org.jetbrains.idea.svn.status.Status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class SvnChangedFile {

  @Nonnull
  private final FilePath myFilePath;
  @Nonnull
  private final Status myStatus;
  @Nullable
  private final String myCopyFromURL;

  public SvnChangedFile(@Nonnull FilePath filePath, @Nonnull Status status) {
    this(filePath, status, null);
  }

  public SvnChangedFile(@Nonnull FilePath filePath, @Nonnull Status status, @Nullable String copyFromURL) {
    myFilePath = filePath;
    myStatus = status;
    myCopyFromURL = copyFromURL;
  }

  @Nonnull
  public FilePath getFilePath() {
    return myFilePath;
  }

  @Nonnull
  public Status getStatus() {
    return myStatus;
  }

  @Nullable
  public String getCopyFromURL() {
    return ObjectUtil.chooseNotNull(myCopyFromURL, myStatus.getCopyFromURL());
  }

  @Override
  public String toString() {
    return myFilePath.getPath();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnChangedFile that = (SvnChangedFile)o;

    return myFilePath.equals(that.myFilePath);
  }

  @Override
  public int hashCode() {
    return myFilePath.hashCode();
  }
}
