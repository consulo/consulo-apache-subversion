package org.jetbrains.idea.svn.properties;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface PropertyClient extends SvnClient {

  @Nullable
  PropertyValue getProperty(@Nonnull final SvnTarget target,
                            @Nonnull final String property,
                            boolean revisionProperty,
                            @Nullable SVNRevision revision) throws VcsException;

  void getProperty(@Nonnull SvnTarget target, @Nonnull String property,
                   @Nullable SVNRevision revision,
                   @Nullable Depth depth,
                   @Nullable PropertyConsumer handler) throws VcsException;

  void list(@Nonnull SvnTarget target,
            @Nullable SVNRevision revision,
            @Nullable Depth depth,
            @Nullable PropertyConsumer handler) throws VcsException;

  void setProperty(@Nonnull File file,
                   @Nonnull String property,
                   @Nullable PropertyValue value,
                   @Nullable Depth depth,
                   boolean force) throws VcsException;

  void setProperties(@Nonnull File file, @Nonnull PropertiesMap properties) throws VcsException;

  void setRevisionProperty(@Nonnull SvnTarget target,
                           @Nonnull String property,
                           @Nonnull SVNRevision revision,
                           @Nullable PropertyValue value,
                           boolean force) throws VcsException;
}
