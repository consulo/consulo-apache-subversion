package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.tmatesoft.svn.core.SVNException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class FileStatusResultParser {

  @Nonnull
  private Pattern myLinePattern;

  @Nullable
  private ProgressTracker handler;

  @Nonnull
  private Convertor<Matcher, ProgressEvent> myConvertor;

  public FileStatusResultParser(@Nonnull Pattern linePattern,
                                @Nullable ProgressTracker handler,
                                @Nonnull Convertor<Matcher, ProgressEvent> convertor) {
    myLinePattern = linePattern;
    this.handler = handler;
    myConvertor = convertor;
  }

  public void parse(@Nonnull String output) throws VcsException {
    if (StringUtil.isEmpty(output)) {
      return;
    }

    for (String line : StringUtil.splitByLines(output)) {
      onLine(line);
    }
  }

  public void onLine(@Nonnull String line) throws VcsException {
    Matcher matcher = myLinePattern.matcher(line);
    if (matcher.matches()) {
      process(matcher);
    }
    else {
      throw new VcsException("unknown state on line " + line);
    }
  }

  public void process(@Nonnull Matcher matcher) throws VcsException {
    if (handler != null) {
      try {
        handler.consume(myConvertor.convert(matcher));
      } catch (SVNException e) {
        throw new VcsException(e);
      }
    }
  }
}
