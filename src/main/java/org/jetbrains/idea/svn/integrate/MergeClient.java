package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface MergeClient extends SvnClient {

  void merge(@Nonnull SvnTarget source,
             @Nonnull File destination,
             boolean dryRun,
             boolean reintegrate,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;

  void merge(@Nonnull SvnTarget source,
             @Nonnull SVNRevisionRange range,
             @Nonnull File destination,
             @Nullable Depth depth,
             boolean dryRun,
             boolean recordOnly,
             boolean force,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;

  void merge(@Nonnull SvnTarget source1,
             @Nonnull SvnTarget source2,
             @Nonnull File destination,
             @Nullable Depth depth,
             boolean useAncestry,
             boolean dryRun,
             boolean recordOnly,
             boolean force,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;
}
