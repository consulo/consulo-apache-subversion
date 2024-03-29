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

import consulo.application.Application;
import consulo.project.Project;
import consulo.project.util.WaitForProgressToShow;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jetbrains.idea.svn.dialogs.ServerSSHDialog;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static consulo.ide.impl.idea.ssh.SSHUtil.PASSPHRASE_PROMPT;
import static consulo.ide.impl.idea.ssh.SSHUtil.PASSWORD_PROMPT;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalSshModule extends BaseTerminalModule {
  private static final Pattern UNKNOWN_HOST_MESSAGE =
    Pattern.compile("The authenticity of host \\'((.*) \\((.*)\\))\\' can\\'t be established\\.\\s?");
  private static final Pattern HOST_FINGERPRINT_MESSAGE = Pattern.compile("(\\w+) key fingerprint is (.*)\\.\\s?");
  private static final Pattern ACCEPT_HOST_PROMPT = Pattern.compile("Are you sure you want to continue connecting \\(yes/no\\)\\?\\s?");

  private String unknownHost;
  private String fingerprintAlgorithm;
  private String hostFingerprint;

  public TerminalSshModule(@Nonnull CommandRuntime runtime, @Nonnull CommandExecutor executor) {
    super(runtime, executor);
  }

  @Override
  public boolean doHandlePrompt(String line, Key outputType) {
    return checkPassphrase(line) || checkPassword(line) || checkUnknownHost(line);
  }

  private boolean checkPassphrase(@Nonnull String line) {
    Matcher matcher = PASSPHRASE_PROMPT.matcher(line);

    return matcher.matches() && handleAuthPrompt(SimpleCredentialsDialog.Mode.SSH_PASSPHRASE, matcher.group(1));
  }

  private boolean checkPassword(@Nonnull String line) {
    Matcher matcher = PASSWORD_PROMPT.matcher(line);

    return matcher.matches() && handleAuthPrompt(SimpleCredentialsDialog.Mode.SSH_PASSWORD, matcher.group(1));
  }

  private boolean checkUnknownHost(@Nonnull String line) {
    Matcher unknownHostMatcher = UNKNOWN_HOST_MESSAGE.matcher(line);
    Matcher hostFingerPrintMatcher = HOST_FINGERPRINT_MESSAGE.matcher(line);
    Matcher acceptHostMatcher = ACCEPT_HOST_PROMPT.matcher(line);

    if (unknownHostMatcher.matches()) {
      unknownHost = unknownHostMatcher.group(1);
    }
    else if (hostFingerPrintMatcher.matches()) {
      fingerprintAlgorithm = hostFingerPrintMatcher.group(1);
      hostFingerprint = hostFingerPrintMatcher.group(2);
    }
    else if (acceptHostMatcher.matches()) {
      handleUnknownHost();
    }

    return unknownHostMatcher.matches() || hostFingerPrintMatcher.matches() || acceptHostMatcher.matches();
  }

  private void handleUnknownHost() {
    final Project project = myRuntime.getVcs().getProject();
    final Ref<Integer> answer = new Ref<>();

    Runnable command = new Runnable() {
      @Override
      public void run() {
        final ServerSSHDialog dialog = new ServerSSHDialog(project, true, unknownHost, fingerprintAlgorithm, hostFingerprint);
        dialog.show();
        answer.set(dialog.getResult());
      }
    };

    // Use ModalityState.any() as currently ssh credentials in terminal mode are requested in the thread that reads output and not in
    // the thread that started progress
    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(command, Application.get().getAnyModalityState());

    unknownHost = null;
    fingerprintAlgorithm = null;
    hostFingerprint = null;

    sendData(answer.get() == ISVNAuthenticationProvider.REJECTED ? "no" : "yes");
  }

  private boolean handleAuthPrompt(@Nonnull final SimpleCredentialsDialog.Mode mode, @Nonnull final String key) {
    SVNURL repositoryUrl = myExecutor.getCommand().requireRepositoryUrl();
    String auth = myRuntime.getAuthenticationService().requestSshCredentials(repositoryUrl.toDecodedString(), mode, key);

    if (!StringUtil.isEmpty(auth)) {
      sendData(auth);
    } else {
      cancelAuthentication();
    }

    return !StringUtil.isEmpty(auth);
  }
}
