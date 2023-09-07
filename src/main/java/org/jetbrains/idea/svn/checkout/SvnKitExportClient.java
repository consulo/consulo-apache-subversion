package org.jetbrains.idea.svn.checkout;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitExportClient extends BaseSvnClient implements ExportClient {

  @Override
  public void export(@Nonnull SvnTarget from,
                     @Nonnull File to,
                     @Nullable SVNRevision revision,
                     @Nullable Depth depth,
                     @Nullable String nativeLineEnd,
                     boolean force,
                     boolean ignoreExternals,
                     @Nullable ProgressTracker handler) throws VcsException {
    SVNUpdateClient client = myVcs.getSvnKitManager().createUpdateClient();

    client.setEventHandler(toEventHandler(handler));
    client.setIgnoreExternals(ignoreExternals);

    try {
      if (from.isFile()) {
        client.doExport(from.getFile(), to, from.getPegRevision(), revision, nativeLineEnd, force, toDepth(depth));
      }
      else {
        client.doExport(from.getURL(), to, from.getPegRevision(), revision, nativeLineEnd, force, toDepth(depth));
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
