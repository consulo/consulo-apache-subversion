package org.jetbrains.idea.svn.difftool;

import consulo.diff.request.DiffRequest;
import consulo.ide.impl.idea.diff.DiffContext;
import consulo.ide.impl.idea.diff.FrameDiffTool.DiffViewer;
import consulo.ide.impl.idea.diff.impl.DiffViewerWrapper;

import javax.annotation.Nonnull;

public class SvnDiffViewerWrapper implements DiffViewerWrapper {
  @Nonnull
  private final DiffRequest myPropertyRequest;

  public SvnDiffViewerWrapper(@Nonnull DiffRequest propertyRequest) {
    myPropertyRequest = propertyRequest;
  }

  @Override
  public DiffViewer createComponent(@Nonnull DiffContext context, @Nonnull DiffRequest request, @Nonnull DiffViewer wrappedViewer) {
    return new SvnDiffViewer(context, myPropertyRequest, wrappedViewer);
  }
}
