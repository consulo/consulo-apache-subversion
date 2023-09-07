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

import consulo.process.CommandLineUtil;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersListUtil;
import consulo.process.local.EnvironmentUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnConfigurationState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Konstantin Kolosovsky.
 */
public class SshTunnelRuntimeModule extends BaseCommandRuntimeModule {

  public static final String DEFAULT_SSH_TUNNEL_VALUE = "$SVN_SSH ssh -q";

  public SshTunnelRuntimeModule(@Nonnull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@Nonnull Command command) throws SvnBindException {
    if (!CommandRuntime.isLocal(command)) {
      if (!SvnConfiguration.SshConnectionType.SUBVERSION_CONFIG.equals(getState().sshConnectionType)) {
        command.put("--config-option", "config:tunnels:ssh=" + StringUtil.notNullize(buildTunnelValue()));
      }
    }
  }

  @Nonnull
  private SvnConfiguration getConfiguration() {
    return myRuntime.getVcs().getSvnConfiguration();
  }

  @Nonnull
  private SvnConfigurationState getState() {
    return getConfiguration().getState();
  }

  @Nullable
  private String buildTunnelValue() {
    String sshPath = getState().sshExecutablePath;
    sshPath = !StringUtil.isEmpty(sshPath) ? sshPath : getExecutablePath(getConfiguration().getSshTunnelSetting());

    return StringUtil
      .join(CommandLineUtil.toCommandLine(sshPath, buildTunnelCommandLine(sshPath).getParametersList().getParameters()), " ");
  }

  @Nonnull
  private GeneralCommandLine buildTunnelCommandLine(@Nonnull String sshPath) {
    GeneralCommandLine result = new GeneralCommandLine(sshPath);
    boolean isPuttyLinkClient = StringUtil.endsWithIgnoreCase(FileUtil.getNameWithoutExtension(sshPath), "plink");
    SvnConfigurationState state = getState();

    // quiet mode
    if (!isPuttyLinkClient) {
      result.addParameter("-q");
    }

    result.addParameters(isPuttyLinkClient ? "-P" : "-p", String.valueOf(state.sshPort));

    if (!StringUtil.isEmpty(state.sshUserName)) {
      result.addParameters("-l", state.sshUserName);
    }

    if (SvnConfiguration.SshConnectionType.PRIVATE_KEY.equals(state.sshConnectionType) && !StringUtil.isEmpty(state.sshPrivateKeyPath)) {
      result.addParameters("-i", FileUtil.toSystemIndependentName(state.sshPrivateKeyPath));
    }

    return result;
  }

  @Nonnull
  public static String getSshTunnelValue(@Nullable String tunnelSetting) {
    tunnelSetting = !StringUtil.isEmpty(tunnelSetting) ? tunnelSetting : DEFAULT_SSH_TUNNEL_VALUE;
    String svnSshVariableName = getSvnSshVariableName(tunnelSetting);
    String svnSshVariableValue = EnvironmentUtil.getValue(svnSshVariableName);

    return !StringUtil.isEmpty(svnSshVariableValue)
           ? svnSshVariableValue
           : !StringUtil.isEmpty(svnSshVariableName) ? tunnelSetting.substring(1 + svnSshVariableName.length()) : tunnelSetting;
  }

  @Nonnull
  public static String getSvnSshVariableName(@Nullable String tunnel) {
    String result = "";

    if (tunnel != null && tunnel.startsWith("$")) {
      result = ObjectUtil.notNull(StringUtil.substringBefore(tunnel, " "), tunnel).substring(1);
    }

    return result;
  }

  @Nonnull
  public static String getExecutablePath(@Nullable String tunnelSetting) {
    // TODO: Add additional platform specific checks
    return StringUtil.notNullize(ContainerUtil.getFirstItem(ParametersListUtil.parse(getSshTunnelValue(tunnelSetting)))).trim();
  }
}
