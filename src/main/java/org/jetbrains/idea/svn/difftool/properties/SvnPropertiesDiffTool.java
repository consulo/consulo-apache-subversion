package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import javax.annotation.Nonnull;

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
