package org.jetbrains.idea.svn.api;

import javax.annotation.Nonnull;

import com.intellij.openapi.util.Version;
import org.jetbrains.idea.svn.WorkingCopyFormat;

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
