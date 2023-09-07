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

import consulo.util.lang.Pair;
import consulo.util.lang.function.PairFunction;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.versionBrowser.ChangeBrowserSettings;
import org.jetbrains.idea.svn.history.LogHierarchyNode;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

import static consulo.application.progress.ProgressManager.progress;
import static consulo.application.progress.ProgressManager.progress2;
import static consulo.util.collection.ContainerUtil.newArrayList;
import static java.lang.Math.min;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache.MergeCheckResult;

public class MergeCalculatorTask extends BaseMergeTask {

  public static final String PROP_BUNCH_SIZE = "idea.svn.quick.merge.bunch.size";
  private final static int BUNCH_SIZE = 100;

  @Nullable private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;
  @Nonnull
  private final OneShotMergeInfoHelper myMergeChecker;
  @Nonnull
  private final List<SvnChangeList> myChangeLists;
  @Nonnull
  private final Consumer<MergeCalculatorTask> myCallback;
  private boolean myAllListsLoaded;

  public MergeCalculatorTask(@Nonnull QuickMerge mergeProcess,
                             @Nullable SvnBranchPointsCalculator.WrapperInvertor copyPoint,
                             @Nonnull Consumer<MergeCalculatorTask> callback) {
    super(mergeProcess);
    myCopyPoint = copyPoint;
    myCallback = callback;
    myChangeLists = newArrayList();
    // TODO: Previously it was configurable - either to use OneShotMergeInfoHelper or BranchInfo as merge checker, but later that logic
    // TODO: was commented (in 80ebdbfea5210f6c998e67ddf28ca9c670fa4efe on 5/28/2010).
    // TODO: Still check if we need to preserve such configuration or it is sufficient to always use OneShotMergeInfoHelper.
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);
  }

  public boolean areAllListsLoaded() {
    return myAllListsLoaded;
  }

  @Nonnull
  public MergeChecker getMergeChecker() {
    return myMergeChecker;
  }

  @Nonnull
  public List<SvnChangeList> getChangeLists() {
    return myChangeLists;
  }

  @Override
  public void run() throws VcsException
  {
    progress("Collecting merge information");
    myMergeChecker.prepare();

    if (myCopyPoint != null) {
      myChangeLists.addAll(getNotMergedChangeLists(getChangeListsAfter(myCopyPoint.getTrue().getTargetRevision())));
      myAllListsLoaded = true;
    }
    else {
      Pair<List<SvnChangeList>, Boolean> loadResult = loadChangeLists(myMergeContext, -1, getBunchSize(-1));

      myChangeLists.addAll(loadResult.first);
      myAllListsLoaded = loadResult.second;
    }

    if (!myChangeLists.isEmpty()) {
      myCallback.accept(this);
    }
    else {
      myMergeProcess.end("Everything is up-to-date", false);
    }
  }

  @Nonnull
  private List<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsAfter(long revision) throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.CHANGE_AFTER = Long.toString(revision);
    settings.USE_CHANGE_AFTER_FILTER = true;

    return getChangeLists(myMergeContext, settings, revision, -1, Pair::create);
  }

  @Nonnull
  private List<SvnChangeList> getNotMergedChangeLists(@Nonnull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    List<SvnChangeList> result = newArrayList();

    progress("Collecting not merged revisions");
    for (Pair<SvnChangeList, LogHierarchyNode> pair : changeLists) {
      SvnChangeList changeList = pair.getFirst();

      progress2(message("progress.text2.processing.revision", changeList.getNumber()));
      if (MergeCheckResult.NOT_MERGED.equals(myMergeChecker.checkList(changeList)) && !myMergeChecker.checkListForPaths(pair.getSecond())) {
        result.add(changeList);
      }
    }

    return result;
  }

  @Nonnull
  public static Pair<List<SvnChangeList>, Boolean> loadChangeLists(@Nonnull MergeContext mergeContext, long beforeRevision, int size)
    throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    if (beforeRevision > 0) {
      settings.CHANGE_BEFORE = String.valueOf(beforeRevision);
      settings.USE_CHANGE_BEFORE_FILTER = true;
    }

    List<SvnChangeList> changeLists = getChangeLists(mergeContext, settings, beforeRevision, size, (changeList, tree) -> changeList);
    return Pair.create(
      changeLists.subList(0, min(size, changeLists.size())),
      changeLists.size() < size + 1);
  }

  public static int getBunchSize(int size) {
    Integer configuredSize = Integer.getInteger(PROP_BUNCH_SIZE);

    return configuredSize != null ? configuredSize : size > 0 ? size : BUNCH_SIZE;
  }

  @Nonnull
  private static <T> List<T> getChangeLists(@Nonnull MergeContext mergeContext,
                                            @Nonnull ChangeBrowserSettings settings,
                                            long revisionToExclude,
                                            int size,
                                            @Nonnull PairFunction<SvnChangeList, LogHierarchyNode, T> resultProvider) throws VcsException {
    List<T> result = newArrayList();

    ((SvnCommittedChangesProvider)mergeContext.getVcs().getCommittedChangesProvider())
      .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(mergeContext.getSourceUrl()),
                                             size > 0 ? size + (revisionToExclude > 0 ? 2 : 1) : 0,
                                             (changeList, tree) -> {
                                               if (revisionToExclude != changeList.getNumber()) {
                                                 result.add(resultProvider.fun(changeList, tree));
                                               }
                                             });

    return result;
  }
}
