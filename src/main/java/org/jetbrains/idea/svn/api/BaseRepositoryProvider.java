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
package org.jetbrains.idea.svn.api;

import javax.annotation.Nonnull;

import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
* @author Konstantin Kolosovsky.
*/
public abstract class BaseRepositoryProvider implements RepositoryProvider {

  @Nonnull
  protected final SvnVcs myVcs;
  @Nonnull
  protected final SvnTarget myTarget;

  public BaseRepositoryProvider(@Nonnull SvnVcs vcs, @Nonnull SvnTarget target) {
    myVcs = vcs;
    myTarget = target;
  }
}
