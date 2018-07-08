/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static org.jetbrains.idea.svn.SvnUtil.checkRepositoryVersion15;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.jetbrains.idea.svn.WorkingCopyFormat.ONE_DOT_EIGHT;
import static org.jetbrains.idea.svn.integrate.SvnBranchPointsCalculator.WrapperInvertor;

import java.io.File;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.actions.BackgroundTaskGroup;
import com.intellij.util.concurrency.Semaphore;

public class QuickMerge extends BackgroundTaskGroup {

  private static final Logger LOG = Logger.getInstance(QuickMerge.class);

  @Nonnull
  private final MergeContext myMergeContext;
  @Nonnull
  private final QuickMergeInteraction myInteraction;
  @Nonnull
  private final Semaphore mySemaphore = new Semaphore();

  public QuickMerge(@Nonnull MergeContext mergeContext, @Nonnull QuickMergeInteraction interaction) {
    super(mergeContext.getProject(), mergeContext.getTitle());
    myMergeContext = mergeContext;
    myInteraction = interaction;
  }

  @Nonnull
  public MergeContext getMergeContext() {
    return myMergeContext;
  }

  @Nonnull
  public QuickMergeInteraction getInteraction() {
    return myInteraction;
  }

  @Override
  public void showErrors() {
    if (!myExceptions.isEmpty()) {
      myInteraction.showErrors(myMergeContext.getTitle(), myExceptions);
    }
  }

  @Override
  public void waitForTasksToFinish() {
    super.waitForTasksToFinish();
    mySemaphore.waitFor();
  }

  @Override
  public void end() {
    super.end();
    mySemaphore.up();
  }

  public void end(@Nonnull String message, boolean isError) {
    LOG.info((isError ? "Error: " : "Info: ") + message);

    clear();
    getApplication().invokeLater(() -> myInteraction.showErrors(message, isError));
  }

  public boolean is18() {
    return myMergeContext.getWcInfo().getFormat().isOrGreater(ONE_DOT_EIGHT);
  }

  public void execute() {
    FileDocumentManager.getInstance().saveAllDocuments();

    mySemaphore.down();
    runInEdt(() -> {
      if (areInSameHierarchy(createUrl(myMergeContext.getSourceUrl()), myMergeContext.getWcInfo().getUrl())) {
        end("Cannot merge from self", true);
      }
      else if (!hasSwitchedRoots() || myInteraction.shouldContinueSwitchedRootFound()) {
        runInBackground("Checking repository capabilities", indicator -> {
          if (supportsMergeInfo()) {
            runInEdt(this::selectMergeVariant);
          }
          else {
            mergeAll(false);
          }
        });
      }
    });
  }

  private void selectMergeVariant() {
    switch (myInteraction.selectMergeVariant()) {
      case all:
        mergeAll(true);
        break;
      case showLatest:
        runInBackground("Loading recent " + myMergeContext.getBranchName() + " revisions", new MergeCalculatorTask(this, null, task ->
          runInEdt(() -> selectRevisionsToMerge(task, false))));
        break;
      case select:
        runInBackground("Looking for branch origin", new LookForBranchOriginTask(this, false, copyPoint ->
          runInBackground("Filtering " + myMergeContext.getBranchName() + " revisions", new MergeCalculatorTask(this, copyPoint, task ->
            runInEdt(() -> selectRevisionsToMerge(task, true))))));
      case cancel:
        break;
    }
  }

  private void selectRevisionsToMerge(@Nonnull MergeCalculatorTask task, boolean allStatusesCalculated) {
    SelectMergeItemsResult result =
      myInteraction.selectMergeItems(task.getChangeLists(), task.getMergeChecker(), allStatusesCalculated, task.areAllListsLoaded());

    switch (result.getResultCode()) {
      case all:
        mergeAll(true);
        break;
      case select:
      case showLatest:
        merge(result.getSelectedLists());
        break;
      case cancel:
        break;
    }
  }

  private void mergeAll(boolean supportsMergeInfo) {
    // merge info is not supported - branch copy point is used to make first sync merge successful (without unnecessary tree conflicts)
    // merge info is supported and svn client < 1.8 - branch copy point is used to determine if sync or reintegrate merge should be performed
    // merge info is supported and svn client >= 1.8 - branch copy point is not used - svn automatically detects if reintegrate is necessary
    if (supportsMergeInfo && is18()) {
      runInEdt(() -> checkReintegrateIsAllowedAndMergeAll(null, true));
    }
    else {
      runInBackground("Looking for branch origin", new LookForBranchOriginTask(this, true, copyPoint ->
        runInEdt(() -> checkReintegrateIsAllowedAndMergeAll(copyPoint, supportsMergeInfo))));
    }
  }

  private void checkReintegrateIsAllowedAndMergeAll(@Nullable WrapperInvertor copyPoint, boolean supportsMergeInfo) {
    boolean reintegrate = copyPoint != null && copyPoint.isInvertedSense();

    if (!reintegrate || myInteraction.shouldReintegrate(copyPoint.inverted().getTarget())) {
      MergerFactory mergerFactory = createMergeAllFactory(reintegrate, copyPoint, supportsMergeInfo);
      String title = "Merging all from " + myMergeContext.getBranchName() + (reintegrate ? " (reintegrate)" : "");

      merge(title, mergerFactory, null);
    }
  }

  private void merge(@Nonnull List<SvnChangeList> changeLists) {
    if (!changeLists.isEmpty()) {
      ChangeListsMergerFactory mergerFactory = new ChangeListsMergerFactory(changeLists, false, false, true);

      merge(myMergeContext.getTitle(), mergerFactory, changeLists);
    }
  }

  private void merge(@Nonnull String title, @Nonnull MergerFactory mergerFactory, @Nullable List<SvnChangeList> changeLists) {
    runInEdt(new LocalChangesPromptTask(this, changeLists, () ->
      runInEdt(new MergeTask(this, () ->
        newIntegrateTask(title, mergerFactory).queue()))));
  }

  @Nonnull
  private Task newIntegrateTask(@Nonnull String title, @Nonnull MergerFactory mergerFactory) {
    return new SvnIntegrateChangesTask(myMergeContext.getVcs(), new WorkingCopyInfo(myMergeContext.getWcInfo().getPath(), true),
                                       mergerFactory, parseUrl(myMergeContext.getSourceUrl()), title, false,
                                       myMergeContext.getBranchName()) {
      @Override
      public void onFinished() {
        super.onFinished();
        mySemaphore.up();
      }
    };
  }

  private boolean hasSwitchedRoots() {
    File currentRoot = myMergeContext.getWcInfo().getRootInfo().getIoFile();

    return myMergeContext.getVcs().getAllWcInfos().stream()
      .filter(info -> NestedCopyType.switched.equals(info.getType()))
      .anyMatch(info -> FileUtil.isAncestor(currentRoot, info.getRootInfo().getIoFile(), true));
  }

  private boolean supportsMergeInfo() {
    return myMergeContext.getWcInfo().getFormat().supportsMergeInfo() &&
           checkRepositoryVersion15(myMergeContext.getVcs(), myMergeContext.getSourceUrl());
  }

  @Nonnull
  private MergerFactory createMergeAllFactory(boolean reintegrate, @Nullable WrapperInvertor copyPoint, boolean supportsMergeInfo) {
    long revision = copyPoint != null
                    ? reintegrate ? copyPoint.getWrapped().getTargetRevision() : copyPoint.getWrapped().getSourceRevision()
                    : -1;

    return (vcs, target, handler, currentBranchUrl, branchName) ->
      new BranchMerger(vcs, currentBranchUrl, myMergeContext.getWcInfo().getPath(), handler, reintegrate, myMergeContext.getBranchName(),
                       revision, supportsMergeInfo);
  }

  private static boolean areInSameHierarchy(@Nonnull SVNURL url1, @Nonnull SVNURL url2) {
    return SVNURLUtil.isAncestor(url1, url2) || SVNURLUtil.isAncestor(url2, url1);
  }
}
