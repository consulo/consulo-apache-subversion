package org.jetbrains.idea.svn.upgrade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public interface UpgradeClient extends SvnClient {

  void upgrade(@Nonnull File path, @Nonnull WorkingCopyFormat format, @Nullable ProgressTracker handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
