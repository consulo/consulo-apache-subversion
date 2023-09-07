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

import consulo.ui.image.Image;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.virtualFileSystem.status.FileStatus;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;

public class ConflictedSvnChange extends Change {
  private final ConflictState myConflictState;
  // also used if not move/rename
  private TreeConflictDescription myBeforeDescription;
  private TreeConflictDescription myAfterDescription;
  // +-
  private final FilePath myTreeConflictMarkHolder;
  private boolean myIsPhantom;

  public ConflictedSvnChange(ContentRevision beforeRevision,
                             ContentRevision afterRevision,
                             final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictedSvnChange(ContentRevision beforeRevision,
                             ContentRevision afterRevision,
                             FileStatus fileStatus,
                             final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision, fileStatus);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictState getConflictState() {
    return myConflictState;
  }

  public void setIsPhantom(boolean isPhantom) {
    myIsPhantom = isPhantom;
  }

  @Override
  public boolean isTreeConflict() {
    return myConflictState.isTree();
  }

  @Override
  public boolean isPhantom() {
    return myIsPhantom;
  }

  public TreeConflictDescription getBeforeDescription() {
    return myBeforeDescription;
  }

  public void setBeforeDescription(TreeConflictDescription beforeDescription) {
    myBeforeDescription = beforeDescription;
  }

  public TreeConflictDescription getAfterDescription() {
    return myAfterDescription;
  }

  public void setAfterDescription(TreeConflictDescription afterDescription) {
    myAfterDescription = afterDescription;
  }

  @Override
  public Image getAdditionalIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    final String description = myConflictState.getDescription();
    if (description != null) {
      final StringBuilder sb = new StringBuilder(SvnBundle.message("svn.changeview.item.in.conflict.text", description));
      if (myBeforeDescription != null) {
        sb.append('\n');
        if (myAfterDescription != null) {
          sb.append("before: ");
        }
        sb.append(myBeforeDescription.toPresentableString());
      }
      if (myAfterDescription != null) {
        sb.append('\n');
        if (myBeforeDescription != null) {
          sb.append("after: ");
        }
        sb.append(myAfterDescription.toPresentableString());
      }
      return sb.toString();
    }
    return description;
  }

  public FilePath getTreeConflictMarkHolder() {
    return myTreeConflictMarkHolder;
  }
}
