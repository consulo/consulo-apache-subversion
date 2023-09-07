package org.jetbrains.idea.svn.lock;

import consulo.versionControlSystem.VcsException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdLockClient extends BaseSvnClient implements LockClient {

  @Override
  public void lock(@Nonnull File file, boolean force, @Nonnull String message, @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = prepareParameters(file, force);

    parameters.add("--message");
    parameters.add(message);

    CommandExecutor command = execute(myVcs, SvnTarget.fromFile(file), SvnCommandName.lock, parameters, null);
    handleCommandCompletion(command, file, EventAction.LOCKED, EventAction.LOCK_FAILED, handler);
  }

  @Override
  public void unlock(@Nonnull File file, boolean force, @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = prepareParameters(file, force);

    CommandExecutor command = execute(myVcs, SvnTarget.fromFile(file), SvnCommandName.unlock, parameters, null);
    handleCommandCompletion(command, file, EventAction.UNLOCKED, EventAction.UNLOCK_FAILED, handler);
  }

  private static List<String> prepareParameters(@Nonnull File file, boolean force) {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, file);
    CommandUtil.put(parameters, force, "--force");

    return parameters;
  }

  private static void handleCommandCompletion(@Nonnull CommandExecutor command,
                                              @Nonnull File file,
                                              @Nonnull EventAction success,
                                              @Nonnull EventAction failure,
                                              @Nullable ProgressTracker handler) throws VcsException
  {
    // just warning appears in output when can not lock/unlock file for some reason (like, that file is already locked)
    SVNErrorMessage error = SvnUtil.parseWarning(command.getErrorOutput());

    try {
      invokeHandler(file, error == null ? success : failure, handler, error);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private static void invokeHandler(@Nonnull File file,
                                    @Nonnull EventAction action,
                                    @Nullable ProgressTracker handler,
                                    @Nullable SVNErrorMessage error)
    throws SVNException {
    if (handler != null) {
      handler.consume(createEvent(file, action, error));
    }
  }

  private static ProgressEvent createEvent(@Nonnull File file, @Nonnull EventAction action, @Nullable SVNErrorMessage error) {
    return new ProgressEvent(file, -1, null, null, action, error, null);
  }
}
