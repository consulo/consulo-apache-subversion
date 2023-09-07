/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessEvent;
import consulo.process.io.BaseOutputReader;
import consulo.process.local.CapturingProcessAdapter;
import consulo.util.collection.Lists;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.svn.SvnUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalProcessHandler extends SvnProcessHandler {

  private final List<InteractiveCommandListener> myInteractiveListeners = Lists.newLockFreeCopyOnWriteList();
  private final CapturingProcessAdapter terminalOutputCapturer = new CapturingProcessAdapter();

  private final StringBuilder outputLine = new StringBuilder();
  private final StringBuilder errorLine = new StringBuilder();

  public TerminalProcessHandler(@Nonnull Process process, @Nonnull String commandLine, boolean forceUtf8, boolean forceBinary) {
    super(process, commandLine, forceUtf8, forceBinary);
  }

  public void addInteractiveListener(@Nonnull InteractiveCommandListener listener) {
    myInteractiveListeners.add(listener);
  }

  @Override
  protected boolean processHasSeparateErrorStream() {
    return false;
  }

  @Override
  protected void destroyProcessImpl() {
    final Process process = getProcess();
    process.destroy();
  }

  @Nonnull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.BLOCKING;
  }

  @Override
  public void notifyTextAvailable(String text, Key outputType) {
    if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
      super.notifyTextAvailable(text, outputType);
    }
    else {
      terminalOutputCapturer.onTextAvailable(new ProcessEvent(this, text), outputType);

      text = filterText(text);

      if (!StringUtil.isEmpty(text)) {
        StringBuilder lastLine = getLastLineFor(outputType);
        String currentLine = lastLine.append(text).toString();
        lastLine.setLength(0);

        currentLine = filterCombinedText(currentLine);

        // check if current line presents some interactive output
        boolean handled = handlePrompt(currentLine, outputType);
        if (!handled) {
          notify(currentLine, outputType, lastLine);
        }
      }
    }
  }

  protected boolean handlePrompt(String text, Key outputType) {
    // if process has separate output and error streams => try to handle prompts only from error stream output
    boolean shouldHandleWithListeners = !processHasSeparateErrorStream() || ProcessOutputTypes.STDERR.equals(outputType);

    return shouldHandleWithListeners && handlePromptWithListeners(text, outputType);
  }

  private boolean handlePromptWithListeners(String text, Key outputType) {
    boolean result = false;

    for (InteractiveCommandListener listener : myInteractiveListeners) {
      result |= listener.handlePrompt(text, outputType);
    }

    return result;
  }

  @Nonnull
  protected String filterCombinedText(@Nonnull String currentLine) {
    return currentLine;
  }

  @Nonnull
  protected String filterText(@Nonnull String text) {
    return text;
  }

  private void notify(@Nonnull String text, @Nonnull Key outputType, @Nonnull StringBuilder lastLine) {
    // text is not more than one line - either one line or part of the line
    if (StringUtil.endsWith(text, "\n")) {
      // we have full line - notify listeners
      super.notifyTextAvailable(text, resolveOutputType(text, outputType));
    }
    else {
      // save line part to lastLine
      lastLine.append(text);
    }
  }

  @Nonnull
  protected Key resolveOutputType(@Nonnull String line, @Nonnull Key outputType) {
    Key result = outputType;

    if (!ProcessOutputTypes.SYSTEM.equals(outputType)) {
      Matcher errorMatcher = SvnUtil.ERROR_PATTERN.matcher(line);
      Matcher warningMatcher = SvnUtil.WARNING_PATTERN.matcher(line);

      result = errorMatcher.find() || warningMatcher.find() ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT;
    }

    return result;
  }

  @Nonnull
  private StringBuilder getLastLineFor(Key outputType) {
    if (ProcessOutputTypes.STDERR.equals(outputType)) {
      return errorLine;
    }
    else if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      return outputLine;
    }
    else {
      throw new IllegalArgumentException("Unknown process output type " + outputType);
    }
  }

  public String getTerminalOutput() {
    return terminalOutputCapturer.getOutput().getStdout();
  }
}
