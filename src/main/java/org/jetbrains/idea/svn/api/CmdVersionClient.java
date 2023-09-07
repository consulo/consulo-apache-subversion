package org.jetbrains.idea.svn.api;

import consulo.process.util.ProcessOutput;
import consulo.util.lang.Version;
import org.jetbrains.idea.svn.commandLine.Command;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdVersionClient extends BaseSvnClient implements VersionClient {

  private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
  private static final int COMMAND_TIMEOUT = 30 * 1000;

  @Nonnull
  @Override
  public Version getVersion() throws SvnBindException {
    return parseVersion(runCommand(true));
  }

  @Nonnull
  public ProcessOutput runCommand(boolean quiet) throws SvnBindException {
    Command command = new Command(SvnCommandName.version);
    if (quiet) {
    command.put("--quiet");
    }

    return newRuntime(myVcs).runLocal(command, COMMAND_TIMEOUT).getProcessOutput();
  }

  @Nonnull
  private static Version parseVersion(@Nonnull ProcessOutput output) throws SvnBindException {
    // TODO: This or similar check should likely go to CommandRuntime - to be applied for all commands
    if (output.isTimeout()) {
      throw new SvnBindException(String.format("Exit code: %d, Error: %s", output.getExitCode(), output.getStderr()));
    }

    return parseVersion(output.getStdout());
  }

  @Nonnull
  public static Version parseVersion(@Nonnull String versionText) throws SvnBindException {
    Version result = null;
    Exception cause = null;

    Matcher matcher = VERSION.matcher(versionText);
    boolean found = matcher.find();

    if (found) {
      try {
        result = new Version(getInt(matcher.group(1)), getInt(matcher.group(2)), getInt(matcher.group(3)));
      }
      catch (NumberFormatException e) {
        cause = e;
      }
    }

    if (!found || cause != null) {
      throw new SvnBindException(String.format("Could not parse svn version: %s", versionText), cause);
    }

    return result;
  }

  private static int getInt(@Nonnull String value) {
    return Integer.parseInt(value);
  }
}
