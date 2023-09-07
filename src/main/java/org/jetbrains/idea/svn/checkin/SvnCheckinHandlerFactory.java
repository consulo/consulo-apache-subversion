/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.checkin;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.PairConsumer;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.CommitExecutor;
import consulo.versionControlSystem.change.LocalCommitExecutor;
import consulo.versionControlSystem.checkin.CheckinHandler;
import consulo.versionControlSystem.checkin.CheckinProjectPanel;
import consulo.versionControlSystem.checkin.VcsCheckinHandlerFactory;
import consulo.versionControlSystem.ui.RefreshableOnComponent;
import consulo.versionControlSystem.update.ActionInfo;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.update.AutoSvnUpdater;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/16/12
 * Time: 6:51 PM
 */
@ExtensionImpl
public class SvnCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public SvnCheckinHandlerFactory() {
    super(SvnVcs.getKey());
  }

  @Nonnull
  @Override
  protected CheckinHandler createVcsHandler(final CheckinProjectPanel panel) {
    final Project project = panel.getProject();
    final Collection<VirtualFile> commitRoots = panel.getRoots();
    return new CheckinHandler() {
      private Collection<Change> myChanges = panel.getSelectedChanges();

      @Override
      public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        return null;
      }

      @Override
      public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        if (executor instanceof LocalCommitExecutor) return ReturnResult.COMMIT;
        final SvnVcs vcs = SvnVcs.getInstance(project);
        final MultiMap<String, WorkingCopyFormat> copiesInfo = splitIntoCopies(vcs, myChanges);
        final List<String> repoUrls = new ArrayList<>();
        for (Map.Entry<String, Collection<WorkingCopyFormat>> entry : copiesInfo.entrySet()) {
          if (entry.getValue().size() > 1) {
            repoUrls.add(entry.getKey());
          }
        }
        if (!repoUrls.isEmpty()) {
          final String join = StringUtil.join(repoUrls, ",\n");
          final int isOk = Messages.showOkCancelDialog(project,
                                                       SvnBundle.message("checkin.different.formats.involved",
                                                                         repoUrls.size() > 1 ? 1 : 0,
                                                                         join),
                                                       "Subversion: Commit Will Split", Messages.getWarningIcon());

          return Messages.OK == isOk ? ReturnResult.COMMIT : ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }

      @Override
      public void includedChangesChanged() {
        myChanges = panel.getSelectedChanges();
      }

      @Override
      public void checkinSuccessful() {
        if (SvnConfiguration.getInstance(project).isAutoUpdateAfterCommit()) {
          final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(SvnVcs.getInstance(project));
          final List<FilePath> paths = new ArrayList<>();
          for (VirtualFile root : roots) {
            boolean take = false;
            for (VirtualFile commitRoot : commitRoots) {
              if (VirtualFileUtil.isAncestor(root, commitRoot, false)) {
                take = true;
                break;
              }
            }
            if (take) {
              paths.add(VcsUtil.getFilePath(root));
            }
          }
          if (paths.isEmpty()) return;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              AutoSvnUpdater.run(new AutoSvnUpdater(project, paths.toArray(new FilePath[paths.size()])), ActionInfo.UPDATE.getActionName());
            }
          }, Application.get().getNoneModalityState());
        }
      }
    };
  }

  @Nonnull
  private static MultiMap<String, WorkingCopyFormat> splitIntoCopies(@Nonnull SvnVcs vcs,
                                                                                             @Nonnull Collection<Change> changes) {
    MultiMap<String, WorkingCopyFormat> result = MultiMap.createSet();
    SvnFileUrlMapping mapping = vcs.getSvnFileUrlMapping();

    for (Change change : changes) {
      RootUrlInfo path = mapping.getWcRootForFilePath(ChangesUtil.getFilePath(change).getIOFile());

      if (path != null) {
        result.putValue(path.getRepositoryUrl(), path.getFormat());
      }
    }

    return result;
  }
}
