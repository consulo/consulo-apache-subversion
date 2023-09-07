package org.jetbrains.idea.svn.add;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface AddClient extends SvnClient {

  void add(@Nonnull File file,
           @Nullable Depth depth,
           boolean makeParents,
           boolean includeIgnored,
           boolean force,
           @Nullable ProgressTracker handler) throws VcsException;
}
