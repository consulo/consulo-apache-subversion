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
package org.jetbrains.idea.svn.status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 9:46 AM
 */
public interface StatusClient extends SvnClient {

  /**
   * TODO: Return value is never used by other code
   */
  long doStatus(@Nonnull File path,
                @Nullable SVNRevision revision,
                @Nonnull Depth depth,
                boolean remote,
                boolean reportAll,
                boolean includeIgnored,
                boolean collectParentExternals,
                @Nonnull StatusConsumer handler,
                @Nullable Collection changeLists) throws SvnBindException;

  @Nullable
  Status doStatus(@Nonnull File path, boolean remote) throws SvnBindException;
}
