package org.jetbrains.idea.svn.annotate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public interface AnnotateClient extends SvnClient {

  void annotate(@Nonnull SvnTarget target,
                @Nonnull SVNRevision startRevision,
                @Nonnull SVNRevision endRevision,
                boolean includeMergedRevisions,
                @Nullable DiffOptions diffOptions,
                @Nullable AnnotationConsumer handler) throws VcsException;
}
