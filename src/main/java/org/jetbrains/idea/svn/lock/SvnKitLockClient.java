package org.jetbrains.idea.svn.lock;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitLockClient extends BaseSvnClient implements LockClient {

  @Override
  public void lock(@Nonnull File file, boolean force, @Nonnull String message, @Nullable ProgressTracker handler) throws VcsException {
    try {
      getClient(handler).doLock(new File[]{file}, force, message);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public void unlock(@Nonnull File file, boolean force, @Nullable ProgressTracker handler) throws VcsException {
    try {
      getClient(handler).doUnlock(new File[]{file}, force);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Nonnull
  private SVNWCClient getClient(@Nullable ProgressTracker handler) {
    SVNWCClient client = myVcs.getSvnKitManager().createWCClient();

    client.setEventHandler(toEventHandler(handler));

    return client;
  }
}
