package org.jetbrains.idea.svn.revert;

import consulo.util.collection.ArrayUtil;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitRevertClient extends BaseSvnClient implements RevertClient {

  @Override
  public void revert(@Nonnull Collection<File> paths,
                     @Nullable Depth depth,
                     @Nullable ProgressTracker handler) throws VcsException {
    SVNWCClient client = myVcs.getSvnKitManager().createWCClient();

    client.setEventHandler(toEventHandler(handler));
    try {
      client.doRevert(ArrayUtil.toObjectArray(paths, File.class), toDepth(depth), null);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
