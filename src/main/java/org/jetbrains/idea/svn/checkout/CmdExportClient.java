package org.jetbrains.idea.svn.checkout;

import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdExportClient extends BaseSvnClient implements ExportClient {

  @Override
  public void export(@Nonnull SvnTarget from,
                     @Nonnull File to,
                     @Nullable SVNRevision revision,
                     @Nullable Depth depth,
                     @Nullable String nativeLineEnd,
                     boolean force,
                     boolean ignoreExternals,
                     @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, from);
    CommandUtil.put(parameters, to);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, force, "--force");
    CommandUtil.put(parameters, ignoreExternals, "--ignore-externals");
    if (!StringUtil.isEmpty(nativeLineEnd)) {
      parameters.add("--native-eol");
      parameters.add(nativeLineEnd);
    }

    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(to, handler);

    execute(myVcs, from, to, SvnCommandName.export, parameters, listener);

    listener.throwWrappedIfException();
  }
}
