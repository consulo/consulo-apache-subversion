package org.jetbrains.idea.svn.copy;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CopyMoveClient extends SvnClient {

  void copy(@Nonnull File src, @Nonnull File dst, boolean makeParents, boolean isMove) throws VcsException;

  /**
   * @param source
   * @param destination
   * @param revision
   * @param makeParents
   * @param isMove
   * @param message
   * @param handler
   * @return new revision number
   * @throws VcsException
   */
  long copy(@Nonnull SvnTarget source,
            @Nonnull SvnTarget destination,
            @Nullable SVNRevision revision,
            boolean makeParents,
            boolean isMove,
            @Nonnull String message,
            @Nullable CommitEventHandler handler) throws VcsException;

  void copy(@Nonnull SvnTarget source,
            @Nonnull File destination,
            @Nullable SVNRevision revision,
            boolean makeParents,
            @Nullable ProgressTracker handler) throws VcsException;
}
