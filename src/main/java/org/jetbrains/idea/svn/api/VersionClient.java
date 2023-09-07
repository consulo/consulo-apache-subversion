package org.jetbrains.idea.svn.api;

import consulo.util.lang.Version;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.annotation.Nonnull;

/**
 * @author Konstantin Kolosovsky.
 */
public interface VersionClient extends SvnClient {

  @Nonnull
  Version getVersion() throws SvnBindException;
}
