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
package org.jetbrains.idea.svn;

import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.util.lang.TimeoutUtil;
import consulo.versionControlSystem.VcsException;
import junit.framework.Assert;
import javax.annotation.Nonnull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.commandLine.*;
import org.junit.Test;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/19/13
 * Time: 11:45 AM
 */
// TODO: Rather strange test - probably it should be removed
public abstract class SvnCommandLineStabilityTest extends Svn17TestCase {

  @Test
  public void testCallInfoManyTimes() throws Exception {
    for (int i = 0; i < 200; i++) {
      call();
      TimeoutUtil.sleep(5);
    }
  }

  private void call() throws VcsException
  {
    String result = runInfo().getOutput();
    System.out.println(result);
    Assert.assertNotNull(result);
  }

  @Nonnull
  private CommandExecutor runInfo() throws SvnBindException {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File workingDirectory = VfsUtilCore.virtualToIoFile(myWorkingCopyDir);
    Command command = new Command(SvnCommandName.info);

    command.setTarget(SvnTarget.fromFile(workingDirectory));
    command.setWorkingDirectory(workingDirectory);
    command.put("--xml");

    CommandRuntime runtime = new CommandRuntime(vcs, new AuthenticationService(vcs, true));
    return runtime.runWithAuthenticationAttempt(command);
  }
}