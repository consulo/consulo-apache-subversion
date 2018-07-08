package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CheckoutClient extends SvnClient {

  void checkout(@Nonnull SvnTarget source,
                @Nonnull File destination,
                @Nullable SVNRevision revision,
                @Nullable Depth depth,
                boolean ignoreExternals,
                boolean force,
                @Nonnull WorkingCopyFormat format,
                @Nullable ProgressTracker handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
