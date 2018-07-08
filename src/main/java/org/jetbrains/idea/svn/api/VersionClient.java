package org.jetbrains.idea.svn.api;

import javax.annotation.Nonnull;

import com.intellij.openapi.util.Version;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

/**
 * @author Konstantin Kolosovsky.
 */
public interface VersionClient extends SvnClient {

  @Nonnull
  Version getVersion() throws SvnBindException;
}
