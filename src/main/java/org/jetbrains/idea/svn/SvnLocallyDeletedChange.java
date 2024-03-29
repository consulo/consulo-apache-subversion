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
import consulo.versionControlSystem.change.LocallyDeletedChange;

import javax.annotation.Nonnull;

public class SvnLocallyDeletedChange extends LocallyDeletedChange
{
  @Nonnull
  private final ConflictState myConflictState;

  public SvnLocallyDeletedChange(@Nonnull FilePath path, @Nonnull ConflictState state) {
    super(path);
    myConflictState = state;
  }

  @Override
  public Image getAddIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    String description = myConflictState.getDescription();

    return description != null ? SvnBundle.message("svn.changeview.locally.deleted.item.in.conflict.text", description) : null;
  }

  @Nonnull
  public ConflictState getConflictState() {
    return myConflictState;
  }
}
