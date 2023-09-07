/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.treeConflict;

import consulo.application.util.function.ThrowableComputable;
import consulo.fileChooser.FileChooserFactory;
import consulo.fileChooser.FileSaverDescriptor;
import consulo.fileChooser.FileSaverDialog;
import consulo.ide.impl.idea.openapi.diff.impl.patch.FilePatch;
import consulo.ide.impl.idea.openapi.diff.impl.patch.PatchSyntaxException;
import consulo.ide.impl.idea.openapi.diff.impl.patch.TextFilePatch;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.ApplyPatchExecutor;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.PatchWriter;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.TextFilePatchInProgress;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.util.WaitForProgressToShow;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.MultiMap;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.ObjectUtil;
import consulo.versionControlSystem.change.CommitContext;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static consulo.application.CommonBundle.getErrorTitle;
import static consulo.util.collection.ContainerUtil.newArrayList;
import static consulo.util.io.FileUtil.getRelativePath;
import static consulo.util.io.FileUtil.toSystemIndependentName;
import static consulo.util.lang.StringUtil.isEmptyOrSpaces;
import static consulo.versionControlSystem.VcsBundle.message;

public class ApplyPatchSaveToFileExecutor implements ApplyPatchExecutor<TextFilePatchInProgress> {
  private static final Logger LOG = Logger.getInstance(ApplyPatchSaveToFileExecutor.class);

  @Nonnull
  private final Project myProject;
  @Nullable
  private final VirtualFile myNewPatchBase;

  public ApplyPatchSaveToFileExecutor(@Nonnull Project project, @Nullable VirtualFile newPatchBase) {
    myProject = project;
    myNewPatchBase = newPatchBase;
  }

  @Override
  public String getName() {
    return "Save Patch to File";
  }

  @Override
  public void apply(@Nonnull List<FilePatch> remaining,
                    @Nonnull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable ThrowableComputable<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(new FileSaverDescriptor("Save Patch to", ""), myProject);
    VirtualFileWrapper targetFile = dialog.save(myProject.getBaseDir(), "TheirsChanges.patch");

    if (targetFile != null) {
      savePatch(patchGroupsToApply, targetFile);
    }
  }

  private void savePatch(@Nonnull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroups,
                         @Nonnull VirtualFileWrapper targetFile) {
    VirtualFile newPatchBase = ObjectUtil.notNull(myNewPatchBase, myProject.getBaseDir());
    try {
      List<FilePatch> textPatches = toOnePatchGroup(patchGroups, newPatchBase);
      PatchWriter.writePatches(myProject, targetFile.getFile().getPath(), newPatchBase.getPath(), textPatches, new CommitContext(),
                               CharsetToolkit.UTF8_CHARSET);
    }
    catch (IOException e) {
      LOG.info(e);
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(
        () -> Messages.showErrorDialog(myProject, message("create.patch.error.title", e.getMessage()), getErrorTitle()), null, myProject);
    }
  }

  @Nonnull
  public static List<FilePatch> toOnePatchGroup(@Nonnull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroups,
                                                @Nonnull VirtualFile newPatchBase) throws IOException {
    List<FilePatch> result = newArrayList();

    for (Map.Entry<VirtualFile, Collection<TextFilePatchInProgress>> entry : patchGroups
      .entrySet()) {
      VirtualFile oldPatchBase = entry.getKey();
      String relativePath = VfsUtilCore.getRelativePath(oldPatchBase, newPatchBase, '/');
      boolean toConvert = !isEmptyOrSpaces(relativePath) && !".".equals(relativePath);

      for (TextFilePatchInProgress patchInProgress : entry.getValue()) {
        TextFilePatch patch = patchInProgress.getPatch();
        if (toConvert) {
          patch.setBeforeName(getNewBaseRelativePath(newPatchBase, oldPatchBase, patch.getBeforeName()));
          patch.setAfterName(getNewBaseRelativePath(newPatchBase, oldPatchBase, patch.getAfterName()));
        }
        result.add(patch);
      }
    }

    return result;
  }

  @Nullable
  private static String getNewBaseRelativePath(@Nonnull VirtualFile newBase,
                                               @Nonnull VirtualFile oldBase,
                                               @Nullable String oldBaseRelativePath) throws IOException {
    return !isEmptyOrSpaces(oldBaseRelativePath)
      ? getRelativePath(newBase.getPath(), getCanonicalPath(oldBase, oldBaseRelativePath), '/')
      : oldBaseRelativePath;
  }

  @Nonnull
  private static String getCanonicalPath(@Nonnull VirtualFile base,
                                         @Nonnull String relativePath) throws IOException {
    return toSystemIndependentName(new File(base.getPath(), relativePath).getCanonicalPath());
  }
}
