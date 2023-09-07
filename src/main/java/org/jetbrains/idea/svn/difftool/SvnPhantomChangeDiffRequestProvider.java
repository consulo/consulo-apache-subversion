package org.jetbrains.idea.svn.difftool;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.component.ProcessCanceledException;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.request.DiffRequest;
import consulo.ide.impl.idea.diff.DiffContext;
import consulo.ide.impl.idea.diff.FrameDiffTool;
import consulo.ide.impl.idea.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import consulo.ide.impl.idea.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import consulo.ide.impl.idea.vcsUtil.UIVcsUtil;
import consulo.project.Project;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.ThreeState;
import consulo.versionControlSystem.change.Change;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

@ExtensionImpl
public class SvnPhantomChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  @Nonnull
  @Override
  public ThreeState isEquals(@Nonnull Change change1, @Nonnull Change change2) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @Nonnull Change change) {
    return change.isPhantom();
  }

  @Nonnull
  @Override
  public DiffRequest process(@Nonnull ChangeDiffRequestProducer presentable,
                             @Nonnull UserDataHolder context,
                             @Nonnull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException {
    indicator.checkCanceled();
    return new SvnPhantomDiffRequest(presentable.getChange());
  }

  public static class SvnPhantomDiffRequest extends DiffRequest {
    @Nonnull
    private final Change myChange;

    public SvnPhantomDiffRequest(@Nonnull Change change) {
      myChange = change;
    }

    @Nullable
    @Override
    public String getTitle() {
      return ChangeDiffRequestProducer.getRequestTitle(myChange);
    }
  }

  public static class SvnPhantomDiffTool implements FrameDiffTool {
    @Nonnull
    @Override
    public String getName() {
      return "SVN phantom changes viewer";
    }

    @Override
    public boolean canShow(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
      return request instanceof SvnPhantomDiffRequest;
    }

    @Nonnull
    @Override
    public DiffViewer createComponent(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
      return new DiffViewer() {
        @Nonnull
        @Override
        public JComponent getComponent() {
          return UIVcsUtil.infoPanel("Technical record",
                                     "This change is recorded because its target file was deleted,\nand some parent directory was copied (or moved) into the new place.");
        }

        @Nullable
        @Override
        public JComponent getPreferredFocusedComponent() {
          return null;
        }

        @Nonnull
        @Override
        public ToolbarComponents init() {
          return new ToolbarComponents();
        }

        @Override
        public void dispose() {
        }
      };
    }
  }
}
