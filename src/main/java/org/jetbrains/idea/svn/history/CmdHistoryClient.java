package org.jetbrains.idea.svn.history;

import consulo.util.collection.ContainerUtil;
import consulo.versionControlSystem.VcsException;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdHistoryClient extends BaseSvnClient implements HistoryClient {

  @Override
  public void doLog(@Nonnull SvnTarget target,
                    @Nonnull SVNRevision startRevision,
                    @Nonnull SVNRevision endRevision,
                    boolean stopOnCopy,
                    boolean discoverChangedPaths,
                    boolean includeMergedRevisions,
                    long limit,
                    @Nullable String[] revisionProperties,
                    @Nullable LogEntryConsumer handler) throws VcsException
  {
    // TODO: add revision properties parameter if necessary

    List<String> parameters =
      prepareCommand(target, startRevision, endRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit);

    try {
      CommandExecutor command = execute(myVcs, target, SvnCommandName.log, parameters, null);
      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      parseOutput(command, handler);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  private static void parseOutput(@Nonnull CommandExecutor command, @Nullable LogEntryConsumer handler)
    throws VcsException, SVNException {
    try {
      LogInfo log = CommandUtil.parse(command.getOutput(), LogInfo.class);

      if (handler != null && log != null) {
        for (LogEntry.Builder entry : log.entries) {
          iterateRecursively(entry, handler);
        }
      }
    }
    catch (JAXBException e) {
      throw new VcsException(e);
    }
  }

  private static void iterateRecursively(@Nonnull LogEntry.Builder entry, @Nonnull LogEntryConsumer handler) throws SVNException {
    handler.consume(entry.build());

    for (LogEntry.Builder childEntry : entry.getChildEntries()) {
      iterateRecursively(childEntry, handler);
    }

    if (entry.hasChildren()) {
      // empty log entry passed to handler to fully correspond to SVNKit behavior.
      handler.consume(LogEntry.EMPTY);
    }
  }

  private static List<String> prepareCommand(@Nonnull SvnTarget target,
                                             @Nonnull SVNRevision startRevision,
                                             @Nonnull SVNRevision endRevision,
                                             boolean stopOnCopy, boolean discoverChangedPaths, boolean includeMergedRevisions, long limit) {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, startRevision, endRevision);
    CommandUtil.put(parameters, stopOnCopy, "--stop-on-copy");
    CommandUtil.put(parameters, discoverChangedPaths, "--verbose");
    CommandUtil.put(parameters, includeMergedRevisions, "--use-merge-history");
    if (limit > 0) {
      parameters.add("--limit");
      parameters.add(String.valueOf(limit));
    }
    parameters.add("--xml");

    return parameters;
  }

  @XmlRootElement(name = "log")
  public static class LogInfo {

    @XmlElement(name = "logentry")
    public List<LogEntry.Builder> entries = ContainerUtil.newArrayList();
  }
}
