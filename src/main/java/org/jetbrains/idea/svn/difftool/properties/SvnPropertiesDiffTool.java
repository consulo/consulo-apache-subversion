package org.jetbrains.idea.svn.difftool.properties;

import consulo.annotation.component.ExtensionImpl;
import consulo.diff.request.DiffRequest;
import consulo.ide.impl.idea.diff.DiffContext;
import consulo.ide.impl.idea.diff.FrameDiffTool;

import javax.annotation.Nonnull;

@ExtensionImpl
public class SvnPropertiesDiffTool implements FrameDiffTool {
  @Nonnull
  @Override
  public String getName() {
    return "SVN properties viewer";
  }

  @Override
  public boolean canShow(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
    return request instanceof SvnPropertiesDiffRequest;
  }

  @Nonnull
  @Override
  public DiffViewer createComponent(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
    return SvnPropertiesDiffViewer.create(context, (SvnPropertiesDiffRequest)request);
  }
}
