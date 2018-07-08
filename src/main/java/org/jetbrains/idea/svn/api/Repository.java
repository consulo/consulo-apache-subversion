package org.jetbrains.idea.svn.api;

import javax.annotation.Nonnull;

import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class Repository {

  @Nonnull
  private final SVNURL myUrl;

  public Repository(@Nonnull SVNURL url) {
    myUrl = url;
  }

  @Nonnull
  public SVNURL getUrl() {
    return myUrl;
  }
}
