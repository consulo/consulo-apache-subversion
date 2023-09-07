/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import consulo.ide.impl.idea.execution.configurations.PtyCommandLine;
import consulo.process.CommandLineUtil;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.util.collection.Lists;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalExecutor extends CommandExecutor {

  private final List<InteractiveCommandListener> myInteractiveListeners = Lists.newLockFreeCopyOnWriteList();

  public TerminalExecutor(@Nonnull @NonNls String exePath, @Nonnull Command command) {
    super(exePath, command);
  }

  public void addInteractiveListener(@Nonnull InteractiveCommandListener listener) {
    myInteractiveListeners.add(listener);
  }

  @Override
  public Boolean wasError() {
    return Boolean.FALSE;
  }

  @Override
  protected void startHandlingStreams() {
    for (InteractiveCommandListener listener : myInteractiveListeners) {
      ((TerminalProcessHandler)myHandler).addInteractiveListener(listener);
    }

    super.startHandlingStreams();
  }

  @Nonnull
  @Override
  protected SvnProcessHandler createProcessHandler() {
    return new TerminalProcessHandler(myProcess, myCommandLine.getCommandLineString(), needsUtf8Output(), false);
  }

  /**
   * TODO: remove this when separate streams for output and errors are implemented for Unix.
   */
  @Nonnull
  @Override
  public ByteArrayOutputStream getBinaryOutput() {
    if (this instanceof WinTerminalExecutor) {
      return super.getBinaryOutput();
    }

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] outputBytes = getOutput().getBytes(StandardCharsets.UTF_8);

    result.write(outputBytes, 0, outputBytes.length);

    return result;
  }

  @Nonnull
  @Override
  protected GeneralCommandLine createCommandLine() {
    return new PtyCommandLine();
  }

  @Nonnull
  @Override
  protected Process createProcess() throws ExecutionException
  {
    List<String> parameters = escapeArguments(buildParameters());

    return createProcess(parameters);
  }

  @Nonnull
  protected List<String> buildParameters() {
    return CommandLineUtil.toCommandLine(myCommandLine.getExePath(), myCommandLine.getParametersList().getList());
  }

  @Nonnull
  protected Process createProcess(@Nonnull List<String> parameters) throws ExecutionException
  {
    try {
      return ((PtyCommandLine)myCommandLine).startProcessWithPty(parameters, false);
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public void logCommand() {
    super.logCommand();

    LOG.info("Terminal output " + ((TerminalProcessHandler)myHandler).getTerminalOutput());
  }

  @Nonnull
  protected List<String> escapeArguments(@Nonnull List<String> arguments) {
    return arguments;
  }
}
