package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ImportClient extends SvnClient {

  long doImport(@Nonnull File path,
                @Nonnull SVNURL url,
                @Nullable Depth depth,
                @Nonnull String message,
                boolean noIgnore,
                @Nullable CommitEventHandler handler,
                @Nullable ISVNCommitHandler commitHandler) throws VcsException;
}
