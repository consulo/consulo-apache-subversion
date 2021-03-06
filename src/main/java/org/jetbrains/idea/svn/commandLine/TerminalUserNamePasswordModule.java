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
package org.jetbrains.idea.svn.commandLine;

import consulo.util.dataholder.Key;
import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nonnull;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalUserNamePasswordModule extends BaseTerminalModule {

  private static final Pattern USER_NAME_PROMPT = Pattern.compile("Username:\\s?");
  private static final Pattern PASSWORD_PROMPT = Pattern.compile("Password for \\'(.*)\\':\\s?");

  private static final Pattern AUTHENTICATION_REALM_MESSAGE = Pattern.compile("Authentication realm: (.*)\\s?");

  private String realm;
  private String userName;
  private SVNPasswordAuthentication authentication;

  public TerminalUserNamePasswordModule(@Nonnull CommandRuntime runtime, @Nonnull CommandExecutor executor) {
    super(runtime, executor);
  }

  @Override
  public boolean doHandlePrompt(String line, Key outputType) {
    return checkRealm(line) || checkUserName(line) || checkPassword(line);
  }

  private boolean checkRealm(@Nonnull String line) {
    Matcher matcher = AUTHENTICATION_REALM_MESSAGE.matcher(line);

    if (matcher.matches()) {
      realm = matcher.group(1);
    }

    return matcher.matches();
  }

  private boolean checkUserName(@Nonnull String line) {
    Matcher matcher = USER_NAME_PROMPT.matcher(line);

    return matcher.matches() && handleAuthPrompt(true);
  }

  private boolean checkPassword(@Nonnull String line) {
    Matcher matcher = PASSWORD_PROMPT.matcher(line);

    if (matcher.matches()) {
      userName = matcher.group(1);
    }

    return matcher.matches() && handleAuthPrompt(false);
  }

  /**
   * User name and password are asked separately by svn, but we show single dialog for both parameters. Also password could be asked first
   * (before any user name prompt) for pre-configured/system user name.
   */
  private boolean handleAuthPrompt(boolean isUserName) {
    SVNURL repositoryUrl = myExecutor.getCommand().requireRepositoryUrl();

    if (needAskAuthentication(isUserName)) {
      // TODO: Probably pass real realm to dialog
      // TODO: Extend interface to pass username to dialog (probably using some kind of previousAuth, like in SVNKit)
      authentication = (SVNPasswordAuthentication)myRuntime.getAuthenticationService()
        .requestCredentials(repositoryUrl, ISVNAuthenticationManager.PASSWORD);
    }

    return sendData(isUserName);
  }

  private boolean needAskAuthentication(boolean isUserName) {
    // "authentication.getUserName()" was provided by user, "userName" was asked by svn. If they are equal but svn still prompts user name -
    // we treat this as "authentication" credentials are incorrect and we need ask user again.
    return authentication == null || isUserName && StringUtil.equals(userName, authentication.getUserName());
  }

  private boolean sendData(boolean isUserName) {
    if (authentication != null) {
      sendData(isUserName ? authentication.getUserName() : authentication.getPassword());
    }
    else {
      cancelAuthentication();
    }

    return authentication != null;
  }
}
