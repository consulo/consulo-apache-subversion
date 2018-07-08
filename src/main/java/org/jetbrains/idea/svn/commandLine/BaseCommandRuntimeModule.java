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
package org.jetbrains.idea.svn.commandLine;

import javax.annotation.Nonnull;

import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.auth.AuthenticationService;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseCommandRuntimeModule implements CommandRuntimeModule {

  @Nonnull
  protected final CommandRuntime myRuntime;
  @Nonnull
  protected final AuthenticationService myAuthenticationService;
  @Nonnull
  protected final SvnVcs myVcs;

  public BaseCommandRuntimeModule(@Nonnull CommandRuntime runtime) {
    myRuntime = runtime;
    myAuthenticationService = runtime.getAuthenticationService();
    myVcs = runtime.getVcs();
  }

  @Override
  public void onStart(@Nonnull Command command) throws SvnBindException {

  }
}
