package org.jetbrains.idea.svn.api;

import consulo.util.lang.Version;
import org.jetbrains.idea.svn.WorkingCopyFormat;

import javax.annotation.Nonnull;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitVersionClient extends BaseSvnClient implements VersionClient {

  @Nonnull
  @Override
  public Version getVersion() {
    return WorkingCopyFormat.ONE_DOT_SEVEN.getVersion();
  }
}
