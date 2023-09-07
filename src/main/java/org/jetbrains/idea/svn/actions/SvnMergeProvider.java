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
package org.jetbrains.idea.svn.actions;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.versionControlSystem.VcsBundle;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.merge.MergeData;
import consulo.versionControlSystem.merge.MergeProvider;
import consulo.versionControlSystem.util.VcsRunnable;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * @author lesya
 * @author yole
 */
public class SvnMergeProvider implements MergeProvider {

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(SvnMergeProvider.class);

  public SvnMergeProvider(final Project project) {
    myProject = project;
  }

  @Nonnull
  public MergeData loadRevisions(@Nonnull final VirtualFile file) throws VcsException {
    final MergeData data = new MergeData();
    VcsRunnable runnable = new VcsRunnable() {
      public void run() throws VcsException {
        File oldFile = null;
        File newFile = null;
        File workingFile = null;
        boolean mergeCase = false;
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        Info info = vcs.getInfo(file);

        if (info != null) {
          oldFile = info.getConflictOldFile();
          newFile = info.getConflictNewFile();
          workingFile = info.getConflictWrkFile();
          mergeCase = workingFile == null || workingFile.getName().contains("working");
          // for debug
          if (workingFile == null) {
            LOG.info("Null working file when merging text conflict for " + file.getPath() + " old file: " + oldFile + " new file: " + newFile);
          }
          if (mergeCase) {
            // this is merge case
            oldFile = info.getConflictNewFile();
            newFile = info.getConflictOldFile();
            workingFile = info.getConflictWrkFile();
          }
          data.LAST_REVISION_NUMBER = new SvnRevisionNumber(info.getRevision());
        }
        else {
          throw new VcsException("Could not get info for " + file.getPath());
        }
        if (oldFile == null || newFile == null || workingFile == null) {
          ByteArrayOutputStream bos = getBaseRevisionContents(vcs, file);
          data.ORIGINAL = bos.toByteArray();
          data.LAST = bos.toByteArray();
          data.CURRENT = readFile(new File(file.getPath()));
        }
        else {
          data.ORIGINAL = readFile(oldFile);
          data.LAST = readFile(newFile);
          data.CURRENT = readFile(workingFile);
        }
        if (mergeCase) {
          final ByteArrayOutputStream contents = getBaseRevisionContents(vcs, file);
          if (!Arrays.equals(contents.toByteArray(), data.ORIGINAL)) {
            // swap base and server: another order of merge arguments
            byte[] original = data.ORIGINAL;
            data.ORIGINAL = data.LAST;
            data.LAST = original;
          }
        }
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, VcsBundle.message("multiple.file.merge.loading.progress.title"), false, myProject);

    return data;
  }

  private ByteArrayOutputStream getBaseRevisionContents(@Nonnull SvnVcs vcs, @Nonnull VirtualFile file) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      byte[] contents = SvnUtil.getFileContents(vcs, SvnTarget.fromFile(new File(file.getPath())), SVNRevision.BASE, SVNRevision.UNDEFINED);
      bos.write(contents);
    }
    catch (VcsException | IOException e) {
      LOG.warn(e);
    }
    return bos;
  }

  private static byte[] readFile(File workingFile) throws VcsException {
    try {
      return Files.readAllBytes(workingFile.toPath());
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  public void conflictResolvedForFile(@Nonnull VirtualFile file) {
    // TODO: Add possibility to resolve content conflicts separately from property conflicts.
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File path = new File(file.getPath());
    try {
      // TODO: Probably false should be passed to "resolveTree", but previous logic used true implicitly
      vcs.getFactory(path).createConflictClient().resolve(path, Depth.EMPTY, false, true, true);
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
    // the .mine/.r## files have been deleted
    final VirtualFile parent = file.getParent();
    if (parent != null) {
      parent.refresh(true, false);
    }
  }

  public boolean isBinary(@Nonnull final VirtualFile file) {
    SvnVcs vcs = SvnVcs.getInstance(myProject);

    try {
      File ioFile = new File(file.getPath());
      PropertyClient client = vcs.getFactory(ioFile).createPropertyClient();

      PropertyValue value = client.getProperty(SvnTarget.fromFile(ioFile), SvnPropertyKeys.SVN_MIME_TYPE, false, SVNRevision.WORKING);
      if (value != null && isBinaryMimeType(value.toString())) {
        return true;
      }
    }
    catch (VcsException e) {
      LOG.warn(e);
    }

    return false;
  }

  private static boolean isBinaryMimeType(@Nonnull String mimeType) {
    return !mimeType.startsWith("text/");
  }
}
