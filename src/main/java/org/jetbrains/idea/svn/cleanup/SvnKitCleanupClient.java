package org.jetbrains.idea.svn.cleanup;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCleanupClient extends BaseSvnClient implements CleanupClient {

  @Override
  public void cleanup(@Nonnull File path, @Nullable ProgressTracker handler) throws VcsException
  {
    SVNWCClient client = myVcs.getSvnKitManager().createWCClient();

    client.setEventHandler(toEventHandler(handler));
    try {
      client.doCleanup(path);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
