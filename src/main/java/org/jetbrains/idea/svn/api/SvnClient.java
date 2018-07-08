package org.jetbrains.idea.svn.api;

import javax.annotation.Nonnull;

import org.jetbrains.idea.svn.SvnVcs;

/**
 * @author Konstantin Kolosovsky.
 */
public interface SvnClient {

  @Nonnull
  SvnVcs getVcs();

  @Nonnull
  ClientFactory getFactory();

  void setVcs(@Nonnull SvnVcs vcs);

  void setFactory(@Nonnull ClientFactory factory);

  void setIsActive(boolean isActive);
}
