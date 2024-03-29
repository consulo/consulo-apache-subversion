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
package org.jetbrains.idea.svn.browse;

import consulo.util.lang.ObjectUtil;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;

/**
 * @author Konstantin Kolosovsky.
 */
public class DirectoryEntry extends BaseNodeDescription implements Comparable<DirectoryEntry> {

  private final String myName;
  @Nonnull
  private final CommitInfo myCommitInfo;
  private final String myPath;
  private final SVNURL myUrl;
  private final SVNURL myRepositoryRoot;

  @Nonnull
  public static DirectoryEntry create(@Nonnull SVNDirEntry entry) {
    return new DirectoryEntry(entry.getURL(), entry.getRepositoryRoot(), entry.getName(), NodeKind.from(entry.getKind()),
                              new CommitInfo.Builder(entry.getRevision(), entry.getDate(), entry.getAuthor()).build(),
                              entry.getRelativePath());
  }

  public DirectoryEntry(SVNURL url,
                        SVNURL repositoryRoot,
                        String name,
                        @Nonnull NodeKind kind,
                        @Nullable CommitInfo commitInfo,
                        String path) {
    super(kind);
    myUrl = url;
    myRepositoryRoot = repositoryRoot;
    myName = name;
    myCommitInfo = ObjectUtil.notNull(commitInfo, CommitInfo.EMPTY);
    myPath = path;
  }

  public SVNURL getUrl() {
    return myUrl;
  }

  public SVNURL getRepositoryRoot() {
    return myRepositoryRoot;
  }

  public String getName() {
    return myName;
  }

  @Nonnull
  public NodeKind getKind() {
    return myKind;
  }

  public Date getDate() {
    return myCommitInfo.getDate();
  }

  public long getRevision() {
    return myCommitInfo.getRevision();
  }

  public String getAuthor() {
    return myCommitInfo.getAuthor();
  }

  public String getRelativePath() {
    return myPath == null ? myName : myPath;
  }

  @Override
  public int compareTo(@Nonnull DirectoryEntry o) {
    int result = getKind().compareTo(o.getKind());

    return result != 0 ? result : myUrl.toString().compareTo(o.getUrl().toString());
  }
}
