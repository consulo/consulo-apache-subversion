/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnProgressCanceller;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandRuntime {

  private static final Logger LOG = Logger.getInstance(CommandRuntime.class);

  @Nonnull
  private final AuthenticationService myAuthenticationService;
  @Nonnull
  private final SvnVcs myVcs;
  @Nonnull
  private final List<CommandRuntimeModule> myModules;
  private final String exePath;

  public CommandRuntime(@Nonnull SvnVcs vcs, @Nonnull AuthenticationService authenticationService) {
    myVcs = vcs;
    myAuthenticationService = authenticationService;

    SvnApplicationSettings settings = SvnApplicationSettings.getInstance();
    exePath = settings.getCommandLinePath();

    myModules = ContainerUtil.newArrayList();
    myModules.add(new CommandParametersResolutionModule(this));
    myModules.add(new ProxyModule(this));
    myModules.add(new SshTunnelRuntimeModule(this));
  }

  @Nonnull
  public CommandExecutor runWithAuthenticationAttempt(@Nonnull Command command) throws SvnBindException {
    try {
      onStart(command);

      boolean repeat = true;
      CommandExecutor executor = null;
      while (repeat) {
        executor = newExecutor(command);
        executor.run();
        repeat = onAfterCommand(executor, command);
      }
      return executor;
    } finally {
      onFinish();
    }
  }

  @Nonnull
  public CommandExecutor runLocal(@Nonnull Command command, int timeout) throws SvnBindException {
    if (command.getWorkingDirectory() == null) {
      command.setWorkingDirectory(CommandParametersResolutionModule.getDefaultWorkingDirectory(myVcs.getProject()));
    }

    CommandExecutor executor = newExecutor(command);

    executor.run(timeout);
    onAfterCommand(executor, command);

    return executor;
  }

  private void onStart(@Nonnull Command command) throws SvnBindException {
    // TODO: Actually command handler should be used as canceller, but currently all handlers use same cancel logic -
    // TODO: - just check progress indicator
    command.setCanceller(new SvnProgressCanceller());

    for (CommandRuntimeModule module : myModules) {
      module.onStart(command);
    }
  }

  private boolean onAfterCommand(@Nonnull CommandExecutor executor, @Nonnull Command command) throws SvnBindException {
    boolean repeat = false;

    // TODO: synchronization does not work well in all cases - sometimes exit code is not yet set and null returned - fix synchronization
    // here we treat null exit code as some non-zero exit code
    final Integer exitCode = executor.getExitCodeReference();
    if (exitCode == null || exitCode != 0) {
      logNullExitCode(executor, exitCode);
      cleanupManualDestroy(executor, command);
      repeat = !StringUtil.isEmpty(executor.getErrorOutput()) ? handleErrorText(executor, command) : handleErrorCode(executor);
    }
    else {
      handleSuccess(executor);
    }

    return repeat;
  }

  private static void handleSuccess(@Nonnull CommandExecutor executor) {
    // could be situations when exit code = 0, but there is info "warning" in error stream for instance, for "svn status"
    // on non-working copy folder
    if (!StringUtil.isEmptyOrSpaces(executor.getErrorOutput())) {
      // here exitCode == 0, but some warnings are in error stream
      LOG.info("Detected warning - " + executor.getErrorOutput());
    }
  }

  private static boolean handleErrorCode(CommandExecutor executor) throws SvnBindException {
    // no errors found in error stream => we treat null exitCode as successful, otherwise exception is thrown
    Integer exitCode = executor.getExitCodeReference();
    if (exitCode != null) {
      // here exitCode != null && exitCode != 0
      LOG.info("Command - " + executor.getCommandText());
      LOG.info("Command output - " + executor.getOutput());

      throw new SvnBindException("Svn process exited with error code: " + exitCode);
    }

    return false;
  }

  private boolean handleErrorText(CommandExecutor executor, Command command) throws SvnBindException {
    final String errText = executor.getErrorOutput().trim();
    final AuthCallbackCase callback = createCallback(errText, command.getRepositoryUrl(), executor instanceof TerminalExecutor);
    // do not handle possible authentication errors if command was manually cancelled
    // force checking if command is cancelled and not just use corresponding value from executor - as there could be cases when command
    // finishes quickly but with some auth error - this way checkCancelled() is not called by executor itself and so command is repeated
    // "infinite" times despite it was cancelled.
    if (!executor.checkCancelled() && callback != null) {
      if (callback.getCredentials(errText)) {
        if (myAuthenticationService.getSpecialConfigDir() != null) {
          command.setConfigDir(myAuthenticationService.getSpecialConfigDir());
        }
        callback.updateParameters(command);
        return true;
      }
    }

    throw new SvnBindException(errText);
  }

  private void cleanupManualDestroy(CommandExecutor executor, Command command) throws SvnBindException {
    if (executor.isManuallyDestroyed()) {
      cleanup(executor, command.getWorkingDirectory());

      String destroyReason = executor.getDestroyReason();
      if (!StringUtil.isEmpty(destroyReason)) {
        throw new SvnBindException(destroyReason);
      }
    }
  }

  private void onFinish() {
    myAuthenticationService.reset();
  }

  private static void logNullExitCode(@Nonnull CommandExecutor executor, @Nullable Integer exitCode) {
    if (exitCode == null) {
      LOG.info("Null exit code returned, but not errors detected " + executor.getCommandText());
    }
  }

  @Nullable
  private AuthCallbackCase createCallback(@Nonnull final String errText, @Nullable final SVNURL url, boolean isUnderTerminal) {
    List<AuthCallbackCase> authCases = ContainerUtil.newArrayList();

    if (isUnderTerminal) {
      // Subversion client does not prompt for proxy credentials (just fails with error) even in terminal mode. So we handle this case the
      // same way as in non-terminal mode - repeat command with new credentials.
      // NOTE: We could also try getting proxy credentials from user in advance (by issuing separate request and asking for credentials if
      // NOTE: required) - not to execute same command several times like it is currently for all other cases in terminal mode. But such
      // NOTE: behaviour is not mandatory for now - so we just use "repeat command" logic.
      authCases.add(new ProxyCallback(myAuthenticationService, url));
      // Same situation (described above) as with proxy settings is here.
      authCases.add(new TwoWaySslCallback(myAuthenticationService, url));
    }
    else {
      authCases.add(new CertificateCallbackCase(myAuthenticationService, url));
      authCases.add(new ProxyCallback(myAuthenticationService, url));
      authCases.add(new TwoWaySslCallback(myAuthenticationService, url));
      authCases.add(new UsernamePasswordCallback(myAuthenticationService, url));
    }

    return ContainerUtil.find(authCases, new Condition<AuthCallbackCase>() {
      @Override
      public boolean value(AuthCallbackCase authCase) {
        return authCase.canHandle(errText);
      }
    });
  }

  private void cleanup(@Nonnull CommandExecutor executor, @Nonnull File workingDirectory) throws SvnBindException {
    if (executor.getCommandName().isWriteable()) {
      File wcRoot = SvnUtil.getWorkingCopyRootNew(workingDirectory);

      // not all commands require cleanup - for instance, some commands operate only with repository - like "svn info <url>"
      // TODO: check if we could "configure" commands (or make command to explicitly ask) if cleanup is required - not to search
      // TODO: working copy root each time
      if (wcRoot != null) {
        Command cleanupCommand = new Command(SvnCommandName.cleanup);
        cleanupCommand.setWorkingDirectory(wcRoot);

        newExecutor(cleanupCommand).run();
      } else {
        LOG.info("Could not execute cleanup for command " + executor.getCommandText());
      }
    }
  }

  @Nonnull
  private CommandExecutor newExecutor(@Nonnull Command command) {
    final CommandExecutor executor;

    if (!myVcs.getSvnConfiguration().isRunUnderTerminal() || isLocal(command)) {
      command.putIfNotPresent("--non-interactive");
      executor = new CommandExecutor(exePath, command);
    }
    else {
      // do not explicitly specify "--force-interactive" as it is not supported in svn 1.7 - commands will be interactive by default as
      // running under terminal
      executor = newTerminalExecutor(command);
      ((TerminalExecutor)executor).addInteractiveListener(new TerminalSshModule(this, executor));
      ((TerminalExecutor)executor).addInteractiveListener(new TerminalSslCertificateModule(this, executor));
      ((TerminalExecutor)executor).addInteractiveListener(new TerminalUserNamePasswordModule(this, executor));
    }

    return executor;
  }

  @Nonnull
  private TerminalExecutor newTerminalExecutor(@Nonnull Command command) {
    return SystemInfo.isWindows
           ? new WinTerminalExecutor(exePath, command)
           : new TerminalExecutor(exePath, command);
  }

  public static boolean isLocal(@Nonnull Command command) {
    return SvnCommandName.version.equals(command.getName()) ||
           SvnCommandName.cleanup.equals(command.getName()) ||
           SvnCommandName.add.equals(command.getName()) ||
           // currently "svn delete" is only applied to local files
           SvnCommandName.delete.equals(command.getName()) ||
           SvnCommandName.revert.equals(command.getName()) ||
           SvnCommandName.resolve.equals(command.getName()) ||
           SvnCommandName.upgrade.equals(command.getName()) ||
           SvnCommandName.changelist.equals(command.getName()) ||
           command.isLocalInfo() || command.isLocalStatus() || command.isLocalProperty() || command.isLocalCat();
  }

  @Nonnull
  public AuthenticationService getAuthenticationService() {
    return myAuthenticationService;
  }

  @Nonnull
  public SvnVcs getVcs() {
    return myVcs;
  }
}
