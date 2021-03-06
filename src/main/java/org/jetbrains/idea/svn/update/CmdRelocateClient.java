package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdRelocateClient extends BaseSvnClient implements RelocateClient {

  @Override
  public void relocate(@Nonnull File copyRoot, @Nonnull String fromPrefix, @Nonnull String toPrefix) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add(fromPrefix);
    parameters.add(toPrefix);
    CommandUtil.put(parameters, copyRoot, false);

    execute(myVcs, SvnTarget.fromFile(copyRoot), SvnCommandName.relocate, parameters, null);
  }
}
