package org.jetbrains.idea.svn.lock;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface LockClient extends SvnClient {

  void lock(@Nonnull File file,
            boolean force,
            @Nonnull String message,
            @Nullable ProgressTracker handler) throws VcsException;

  void unlock(@Nonnull File file,
              boolean force,
              @Nullable ProgressTracker handler) throws VcsException;
}
