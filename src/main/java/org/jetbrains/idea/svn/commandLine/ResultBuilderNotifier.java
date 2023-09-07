package org.jetbrains.idea.svn.commandLine;

import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.util.LineHandlerHelper;

import javax.annotation.Nonnull;
import java.util.Iterator;

/**
 * @author Konstantin Kolosovsky.
 */
public class ResultBuilderNotifier extends ProcessAdapter {

  /**
   * the partial line from stdout stream
   */
  @Nonnull
  private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  @Nonnull
  private final StringBuilder myStderrLine = new StringBuilder();

  @Nonnull
  private final LineCommandListener myResultBuilder;

  public ResultBuilderNotifier(@Nonnull LineCommandListener resultBuilder) {
    myResultBuilder = resultBuilder;
  }

  public void processTerminated(final ProcessEvent event) {
    try {
      forceNewLine();
    }
    finally {
      myResultBuilder.processTerminated(event.getExitCode());
    }
  }

  private void forceNewLine() {
    if (myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }

  public void onTextAvailable(final ProcessEvent event, final Key outputType) {
    onTextAvailable(event.getText(), outputType);
  }

  private void onTextAvailable(final String text, final Key outputType) {
    Iterator<String> lines = LineHandlerHelper.splitText(text).iterator();
    if (ProcessOutputTypes.STDOUT == outputType) {
      notifyLines(outputType, lines, myStdoutLine);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      notifyLines(outputType, lines, myStderrLine);
    }
  }

  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (!lines.hasNext()) return;
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        notifyLine(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = null;
      if (lines.hasNext()) {
        line = lines.next();
      }

      if (lines.hasNext()) {
        notifyLine(line, outputType);
      }
      else {
        if (line != null && line.length() > 0) {
          lineBuilder.append(line);
        }
        break;
      }
    }
  }

  private void notifyLine(final String line, final Key outputType) {
    myResultBuilder.onLineAvailable(LineHandlerHelper.trimLineSeparator(line), outputType);
  }
}
