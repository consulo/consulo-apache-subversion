package org.jetbrains.idea.svn.difftool;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.BackgroundTaskQueue;
import consulo.component.ProcessCanceledException;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.request.DiffRequest;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.diff.DiffContext;
import consulo.ide.impl.idea.diff.FrameDiffTool;
import consulo.ide.impl.idea.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import consulo.ide.impl.idea.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import consulo.project.Project;
import consulo.ui.ex.awt.Wrapper;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.ThreeState;
import consulo.versionControlSystem.change.Change;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.treeConflict.TreeConflictRefreshablePanel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

@ExtensionImpl
public class SvnTreeConflictDiffRequestProvider implements ChangeDiffRequestProvider {
  @Nonnull
  @Override
  public ThreeState isEquals(@Nonnull Change change1, @Nonnull Change change2) {
    if (change1 instanceof ConflictedSvnChange && change2 instanceof ConflictedSvnChange) {
      if (!change1.isTreeConflict() && !change2.isTreeConflict()) return ThreeState.UNSURE;
      if (!change1.isTreeConflict() || !change2.isTreeConflict()) return ThreeState.NO;

      TreeConflictDescription description1 = ((ConflictedSvnChange)change1).getBeforeDescription();
      TreeConflictDescription description2 = ((ConflictedSvnChange)change2).getBeforeDescription();
      return TreeConflictRefreshablePanel.descriptionsEqual(description1, description2) ? ThreeState.YES : ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @Nonnull Change change) {
    return change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).getConflictState().isTree();
  }

  @Nonnull
  @Override
  public DiffRequest process(@Nonnull ChangeDiffRequestProducer presentable,
                             @Nonnull UserDataHolder context,
                             @Nonnull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException {
    return new SvnTreeConflictDiffRequest(((ConflictedSvnChange)presentable.getChange()));
  }

  public static class SvnTreeConflictDiffRequest extends DiffRequest {
    @Nonnull
    private final ConflictedSvnChange myChange;

    public SvnTreeConflictDiffRequest(@Nonnull ConflictedSvnChange change) {
      myChange = change;
    }

    @Nonnull
    public ConflictedSvnChange getChange() {
      return myChange;
    }

    @Nullable
    @Override
    public String getTitle() {
      return ChangeDiffRequestProducer.getRequestTitle(myChange);
    }
  }

  public static class SvnTreeConflictDiffTool implements FrameDiffTool {
    @Nonnull
    @Override
    public String getName() {
      return "SVN tree conflict viewer";
    }

    @Override
    public boolean canShow(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
      return request instanceof SvnTreeConflictDiffRequest;
    }

    @Nonnull
    @Override
    public DiffViewer createComponent(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
      return new SvnTreeConflictDiffViewer(context, (SvnTreeConflictDiffRequest)request);
    }
  }

  private static class SvnTreeConflictDiffViewer implements FrameDiffTool.DiffViewer {
    @Nonnull
    private final DiffContext myContext;
    @Nonnull
    private final SvnTreeConflictDiffRequest myRequest;
    @Nonnull
    private final Wrapper myPanel = new Wrapper();

    @Nonnull
    private final BackgroundTaskQueue myQueue;
    @Nonnull
    private final TreeConflictRefreshablePanel myDelegate;

    public SvnTreeConflictDiffViewer(@Nonnull DiffContext context, @Nonnull SvnTreeConflictDiffRequest request) {
      myContext = context;
      myRequest = request;

      myQueue = new BackgroundTaskQueue(Application.get(), myContext.getProject(), "Loading change details");

      // We don't need to listen on File/Document, because panel always will be the same for a single change.
      // And if Change will change - we'll create new DiffRequest and DiffViewer
      myDelegate =
        new TreeConflictRefreshablePanel(myContext.getProject(), "Loading tree conflict details", myQueue, myRequest.getChange());
      myDelegate.refresh();
      myPanel.setContent(myDelegate.getPanel());
    }

    @Nonnull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myPanel;
    }

    @Nonnull
    @Override
    public FrameDiffTool.ToolbarComponents init() {
      return new FrameDiffTool.ToolbarComponents();
    }

    @Override
    public void dispose() {
      myQueue.clear();
      Disposer.dispose(myDelegate);
    }
  }
}
