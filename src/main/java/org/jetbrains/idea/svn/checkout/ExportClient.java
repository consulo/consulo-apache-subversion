package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ExportClient extends SvnClient {

  void export(@Nonnull SvnTarget from,
              @Nonnull File to,
              @Nullable SVNRevision revision,
              @Nullable Depth depth,
              @Nullable String nativeLineEnd,
              boolean force,
              boolean ignoreExternals,
              @Nullable ProgressTracker handler) throws VcsException;
}
