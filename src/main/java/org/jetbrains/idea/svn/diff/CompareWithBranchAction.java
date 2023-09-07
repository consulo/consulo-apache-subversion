/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import consulo.application.dumb.DumbAware;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.status.FileStatusManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public class CompareWithBranchAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

    SelectBranchPopup
      .show(project, virtualFile, new MyBranchSelectedCallback(virtualFile), SvnBundle.message("compare.with.branch.popup.title"));
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabled(isEnabled(project, virtualFile));
  }

  private static boolean isEnabled(final Project project, final VirtualFile virtualFile) {
    if (project == null || virtualFile == null) {
      return false;
    }
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }
    return true;
  }

  private static class MyBranchSelectedCallback implements SelectBranchPopup.BranchSelectedCallback {

    @Nonnull
	private final VirtualFile myVirtualFile;

    public MyBranchSelectedCallback(@Nonnull VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
    }

    public void branchSelected(Project project, SvnBranchConfigurationNew configuration, String url, long revision) {
      ElementWithBranchComparer comparer =
        myVirtualFile.isDirectory()
        ? new DirectoryWithBranchComparer(project, myVirtualFile, url, revision)
        : new FileWithBranchComparer(project, myVirtualFile, url, revision);

      comparer.run();
    }
  }
}
