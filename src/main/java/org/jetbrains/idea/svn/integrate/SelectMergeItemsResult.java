/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.List;

public class SelectMergeItemsResult {
  @Nonnull
  private final QuickMergeContentsVariants myResultCode;
  @Nonnull
  private final List<SvnChangeList> myLists;

  public SelectMergeItemsResult(@Nonnull QuickMergeContentsVariants resultCode, @Nonnull List<SvnChangeList> lists) {
    myResultCode = resultCode;
    myLists = lists;
  }

  @Nonnull
  public QuickMergeContentsVariants getResultCode() {
    return myResultCode;
  }

  @Nonnull
  public List<SvnChangeList> getSelectedLists() {
    return myLists;
  }
}
