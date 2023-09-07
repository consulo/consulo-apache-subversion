package org.jetbrains.idea.svn.conflict;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ConflictClient extends SvnClient {

  void resolve(@Nonnull File path, @Nullable Depth depth, boolean resolveProperty, boolean resolveContent, boolean resolveTree)
    throws VcsException;
}
