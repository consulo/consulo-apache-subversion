package org.jetbrains.idea.svn.commandLine;

import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandOutputLogger extends ProcessAdapter {

  private static final Logger LOG = Logger.getInstance(CommandOutputLogger.class);

  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
    String line =  event.getText();

    if (LOG.isDebugEnabled()) {
      LOG.debug(line);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      System.out.print(line);
    }
  }
}
