package org.jetbrains.idea.svn.content;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ContentClient extends SvnClient {

  byte[] getContent(@Nonnull SvnTarget target, @Nullable SVNRevision revision, @Nullable SVNRevision pegRevision)
    throws VcsException, FileTooBigRuntimeException;
}
