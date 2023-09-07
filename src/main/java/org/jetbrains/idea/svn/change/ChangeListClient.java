package org.jetbrains.idea.svn.change;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ChangeListClient extends SvnClient {

  void add(@Nonnull String changeList, @Nonnull File path, @Nullable String[] changeListsToOperate) throws VcsException;

  void remove(@Nonnull File path) throws VcsException;
}
