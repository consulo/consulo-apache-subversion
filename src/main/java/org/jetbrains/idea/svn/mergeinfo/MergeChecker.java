package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Collection;

public interface MergeChecker {

  void prepare() throws VcsException;

  @Nonnull
  SvnMergeInfoCache.MergeCheckResult checkList(@Nonnull SvnChangeList changeList);

  // if nothing, maybe all not merged or merged: here only partly not merged
  @Nullable
  Collection<String> getNotMergedPaths(@Nonnull SvnChangeList changeList);
}
