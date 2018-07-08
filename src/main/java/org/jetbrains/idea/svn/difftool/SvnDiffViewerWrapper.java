package org.jetbrains.idea.svn.difftool;

import javax.annotation.Nonnull;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.diff.requests.DiffRequest;

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
