package org.jetbrains.idea.svn.conflict;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitConflictClient extends BaseSvnClient implements ConflictClient {
  @Override
  public void resolve(@Nonnull File path,
                      @Nullable Depth depth,
                      boolean resolveProperty,
                      boolean resolveContent,
                      boolean resolveTree) throws VcsException {
    try {
      myVcs.getSvnKitManager().createWCClient()
        .doResolve(path, toDepth(depth), resolveContent, resolveProperty, resolveTree, SVNConflictChoice.MERGED);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
