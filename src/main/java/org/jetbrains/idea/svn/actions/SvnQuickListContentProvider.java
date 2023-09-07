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
package org.jetbrains.idea.svn.actions;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.action.VcsQuickListContentProvider;
import org.jetbrains.idea.svn.SvnVcs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
@ExtensionImpl
public class SvnQuickListContentProvider implements VcsQuickListContentProvider {
  public List<AnAction> getVcsActions(@Nullable Project project, @Nullable AbstractVcs activeVcs,
                                      @Nullable DataContext dataContext) {

    if (activeVcs == null || !SvnVcs.VCS_NAME.equals(activeVcs.getName())) {
      return null;
    }

    final ActionManager manager = ActionManager.getInstance();
    final List<AnAction> actions = new ArrayList<>();
    add("Subversion.Copy", manager, actions);
    add("Subversion.Clenaup", manager, actions);
    return actions;
  }

  public List<AnAction> getNotInVcsActions(@Nullable Project project, @Nullable DataContext dataContext) {
    final AnAction action = ActionManager.getInstance().getAction("Subversion.ImportToSVNRepository");
    assert action != null;
    return Collections.singletonList(action);
  }

  public boolean replaceVcsActionsFor(@Nonnull AbstractVcs activeVcs, @Nullable DataContext dataContext) {
    return false;
  }

  private void add(String actionName, ActionManager manager, List<AnAction> actions) {
    final AnAction action = manager.getAction(actionName);
    assert action != null;
    actions.add(action);
  }
}
