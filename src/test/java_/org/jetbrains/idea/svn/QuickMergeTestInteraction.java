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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.integrate.LocalChangesAction;
import org.jetbrains.idea.svn.integrate.QuickMergeContentsVariants;
import org.jetbrains.idea.svn.integrate.QuickMergeInteraction;
import org.jetbrains.idea.svn.integrate.SelectMergeItemsResult;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;
import java.util.function.Function;

import static consulo.util.collection.ContainerUtil.isEmpty;
import static consulo.util.collection.ContainerUtil.newArrayList;
import static consulo.ide.impl.idea.util.containers.ContainerUtilRt.emptyList;

public class QuickMergeTestInteraction implements QuickMergeInteraction {

  private QuickMergeContentsVariants myMergeVariant = QuickMergeContentsVariants.all;
  private final boolean myReintegrateAnswer;
  @Nullable
  private final Function.Mono<List<SvnChangeList>> mySelectedListsProvider;
  @Nonnull
  private final List<Exception> myExceptions;

  public QuickMergeTestInteraction(boolean reintegrate, @Nullable Function.Mono<List<SvnChangeList>> selectedListsProvider) {
    myReintegrateAnswer = reintegrate;
    mySelectedListsProvider = selectedListsProvider;
    myExceptions = newArrayList();
  }

  @Nonnull
  @Override
  public QuickMergeContentsVariants selectMergeVariant() {
    return myMergeVariant;
  }

  public void setMergeVariant(@Nonnull QuickMergeContentsVariants mergeVariant) {
    myMergeVariant = mergeVariant;
  }

  @Override
  public boolean shouldContinueSwitchedRootFound() {
    return false;
  }

  @Override
  public boolean shouldReintegrate(@Nonnull String targetUrl) {
    return myReintegrateAnswer;
  }

  @Nonnull
  @Override
  public SelectMergeItemsResult selectMergeItems(@Nonnull List<SvnChangeList> lists,
                                                 @Nonnull MergeChecker mergeChecker,
                                                 boolean allStatusesCalculated,
                                                 boolean allListsLoaded) {
    return new SelectMergeItemsResult(
      mySelectedListsProvider != null ? QuickMergeContentsVariants.select : QuickMergeContentsVariants.all,
      mySelectedListsProvider != null ? mySelectedListsProvider.fun(lists) : emptyList()
    );
  }

  @Nonnull
  @Override
  public LocalChangesAction selectLocalChangesAction(boolean mergeAll) {
    return LocalChangesAction.continueMerge;
  }

  @Override
  public void showIntersectedLocalPaths(@Nonnull List<FilePath> paths) {
  }

  @Override
  public void showErrors(@Nonnull String message, @Nonnull List<VcsException> exceptions) {
    if (!isEmpty(exceptions)) {
      myExceptions.addAll(exceptions);
    }
    else {
      myExceptions.add(new RuntimeException(message));
    }
  }

  @Override
  public void showErrors(@Nonnull String message, boolean isError) {
    if (isError) {
      myExceptions.add(new RuntimeException(message));
    } else {
      System.out.println("merge warning: " + message);
    }
  }

  public void throwIfExceptions() throws Exception {
    if (!myExceptions.isEmpty()) {
      throw myExceptions.get(0);
    }
  }
}
