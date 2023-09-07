package org.jetbrains.idea.svn.update;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitRelocateClient extends BaseSvnClient implements RelocateClient {

  @Override
  public void relocate(@Nonnull File copyRoot, @Nonnull String fromPrefix, @Nonnull String toPrefix) throws VcsException {
    try {
      myVcs.getSvnKitManager().createUpdateClient()
        .doRelocate(copyRoot, SVNURL.parseURIEncoded(fromPrefix), SVNURL.parseURIEncoded(toPrefix), true);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
