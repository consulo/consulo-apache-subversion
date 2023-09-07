package org.jetbrains.idea.svn.revert;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

/**
 * @author Konstantin Kolosovsky.
 */
public interface RevertClient extends SvnClient {

  void revert(@Nonnull Collection<File> paths, @Nullable Depth depth, @Nullable ProgressTracker handler) throws VcsException;
}
