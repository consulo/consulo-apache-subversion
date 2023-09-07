package org.jetbrains.idea.svn.cleanup;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CleanupClient extends SvnClient {

  void cleanup(@Nonnull File path, @Nullable ProgressTracker handler) throws VcsException;
}
