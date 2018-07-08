package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface DeleteClient extends SvnClient {

  void delete(@Nonnull File path, boolean force, boolean dryRun, @Nullable ProgressTracker handler) throws VcsException;

  long delete(@Nonnull SVNURL url, @Nonnull String message) throws VcsException;
}
