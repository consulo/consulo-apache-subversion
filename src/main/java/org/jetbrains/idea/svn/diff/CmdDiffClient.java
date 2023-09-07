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
package org.jetbrains.idea.svn.diff;

import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.CurrentContentRevision;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.status.FileStatus;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.*;
import org.jetbrains.idea.svn.SvnStatusConvertor;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.jetbrains.idea.svn.status.SvnStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdDiffClient extends BaseSvnClient implements DiffClient {

  @Nonnull
  @Override
  public List<Change> compare(@Nonnull SvnTarget target1, @Nonnull SvnTarget target2) throws VcsException {
    assertUrl(target1);
    if (target2.isFile()) {
      // Such combination (file and url) with "--summarize" option is supported only in svn 1.8.
      // For svn 1.7 "--summarize" is only supported when both targets are repository urls.
      assertDirectory(target2);

      WorkingCopyFormat format = WorkingCopyFormat.from(myFactory.createVersionClient().getVersion());
      if (format.less(WorkingCopyFormat.ONE_DOT_EIGHT)) {
        throw new SvnBindException("Could not compare local file and remote url with executable for svn " + format);
      }
    }

    List<String> parameters = new ArrayList<>();
    CommandUtil.put(parameters, target1);
    CommandUtil.put(parameters, target2);
    parameters.add("--xml");
    parameters.add("--summarize");

    CommandExecutor executor = execute(myVcs, target1, SvnCommandName.diff, parameters, null);
    return parseOutput(target1, target2, executor);
  }

  @Override
  public void unifiedDiff(@Nonnull SvnTarget target1, @Nonnull SvnTarget target2, @Nonnull OutputStream output) throws VcsException
  {
    assertUrl(target1);
    assertUrl(target2);

    List<String> parameters = ContainerUtil.newArrayList();
    CommandUtil.put(parameters, target1);
    CommandUtil.put(parameters, target2);

    CommandExecutor executor = execute(myVcs, target1, SvnCommandName.diff, parameters, null);

    try {
      executor.getBinaryOutput().writeTo(output);
    }
    catch (IOException e) {
      throw new SvnBindException(e);
    }
  }

  @Nonnull
  private List<Change> parseOutput(@Nonnull SvnTarget target1, @Nonnull SvnTarget target2, @Nonnull CommandExecutor executor)
    throws SvnBindException {
    try {
      DiffInfo diffInfo = CommandUtil.parse(executor.getOutput(), DiffInfo.class);
      List<Change> result = ContainerUtil.newArrayList();

      if (diffInfo != null) {
        for (DiffPath path : diffInfo.diffPaths) {
          result.add(createChange(target1, target2, path));
        }
      }

      return result;
    }
    catch (JAXBException e) {
      throw new SvnBindException(e);
    }
  }

  @Nonnull
  private ContentRevision createRevision(@Nonnull FilePath path,
                                         @Nonnull FilePath localPath,
                                         @Nonnull SVNRevision revision,
                                         @Nonnull FileStatus status) {
    ContentRevision result;

    if (path.isNonLocal()) {
    // explicitly use local path for deleted items - so these items will be correctly displayed as deleted under local working copy node
    // and not as deleted under remote branch node (in ChangesBrowser)
    // NOTE, that content is still retrieved using remotePath.
      result = SvnRepositoryContentRevision.create(myVcs, path, status == FileStatus.DELETED ? localPath : null, revision.getNumber());
    }
    else {
      result = CurrentContentRevision.create(path);
  }

    return result;
  }

  private static FilePath createFilePath(@Nonnull SvnTarget target, boolean isDirectory) {
    return target.isFile()
           ? VcsUtil.getFilePath(target.getFile(), isDirectory)
           : VcsUtil.getFilePathOnNonLocal(SvnUtil.toDecodedString(target), isDirectory);
  }

  @Nonnull
  private Change createChange(@Nonnull SvnTarget target1, @Nonnull SvnTarget target2, @Nonnull DiffPath diffPath) throws SvnBindException {
    // TODO: 1) Unify logic of creating Change instance with SvnDiffEditor and SvnChangeProviderContext
    // TODO: 2) If some directory is switched, files inside it are returned as modified in "svn diff --summarize", even if they are equal
    // TODO: to branch files by content - possibly add separate processing of all switched files
    // TODO: 3) Properties change is currently not added as part of result change like in SvnChangeProviderContext.patchWithPropertyChange

    SvnTarget subTarget1 = SvnUtil.append(target1, diffPath.path, true);
    String relativePath = SvnUtil.getRelativeUrl(SvnUtil.toDecodedString(target1), SvnUtil.toDecodedString(subTarget1));

    if (relativePath == null) {
      throw new SvnBindException("Could not get relative path for " + target1 + " and " + subTarget1);
    }

    SvnTarget subTarget2 = SvnUtil.append(target2, FileUtil.toSystemIndependentName(relativePath));

    FilePath target1Path = createFilePath(subTarget1, diffPath.isDirectory());
    FilePath target2Path = createFilePath(subTarget2, diffPath.isDirectory());

    FileStatus status = SvnStatusConvertor
      .convertStatus(SvnStatusHandler.getStatus(diffPath.itemStatus), SvnStatusHandler.getStatus(diffPath.propertiesStatus));

    // statuses determine changes needs to be done to "target1" to get "target2" state
    ContentRevision beforeRevision = status == FileStatus.ADDED
                                     ? null
                                     : createRevision(target1Path, target2Path, target1.getPegRevision(), status);
    ContentRevision afterRevision = status == FileStatus.DELETED
                                    ? null
                                    : createRevision(target2Path, target1Path, target2.getPegRevision(), status);

    return createChange(status, beforeRevision, afterRevision);
  }

  @Nonnull
  private static Change createChange(@Nonnull final FileStatus status,
                                     @Nullable final ContentRevision beforeRevision,
                                     @Nullable final ContentRevision afterRevision) {
    // isRenamed() and isMoved() are always false here not to have text like "moved from ..." in changes window - by default different
    // paths in before and after revisions are treated as move, but this is not the case for "Compare with Branch"
    return new Change(beforeRevision, afterRevision, status) {
      @Override
      public boolean isRenamed() {
        return false;
      }

      @Override
      public boolean isMoved() {
        return false;
      }
    };
  }

  @XmlRootElement(name = "diff")
  public static class DiffInfo {

    @XmlElementWrapper(name = "paths")
    @XmlElement(name = "path")
    public List<DiffPath> diffPaths = new ArrayList<>();
  }

  public static class DiffPath {

    @XmlAttribute(name = "kind", required = true)
    public NodeKind kind;

    @XmlAttribute(name = "props")
    public String propertiesStatus;

    @XmlAttribute(name = "item")
    public String itemStatus;

    @XmlValue
    public String path;

    public boolean isDirectory() {
      return kind.isDirectory();
    }
  }
}
