package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface RelocateClient extends SvnClient {

  void relocate(@Nonnull File copyRoot, @Nonnull String fromPrefix, @Nonnull String toPrefix) throws VcsException;
}
