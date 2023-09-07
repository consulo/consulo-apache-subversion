package org.jetbrains.idea.svn.history;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Konstantin Kolosovsky.
 */
public interface HistoryClient extends SvnClient {

  void doLog(@Nonnull SvnTarget target,
             @Nonnull SVNRevision startRevision,
             @Nonnull SVNRevision endRevision,
             boolean stopOnCopy,
             boolean discoverChangedPaths,
             boolean includeMergedRevisions,
             long limit,
             @Nullable String[] revisionProperties,
             @Nullable LogEntryConsumer handler) throws VcsException;
}
