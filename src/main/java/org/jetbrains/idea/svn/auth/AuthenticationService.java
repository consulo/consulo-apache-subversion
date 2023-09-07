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
package org.jetbrains.idea.svn.auth;

import consulo.application.Application;
import consulo.application.util.TempFileService;
import consulo.application.util.registry.Registry;
import consulo.http.CertificateManager;
import consulo.http.HttpProxyManager;
import consulo.http.IdeHttpClientHelpers;
import consulo.http.impl.internal.proxy.CommonProxy;
import consulo.ide.impl.idea.openapi.util.Getter;
import consulo.logging.Logger;
import consulo.project.util.WaitForProgressToShow;
import consulo.ui.NotificationType;
import consulo.ui.ex.awt.util.PopupUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.security.KeyManagementException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/26/13
 * Time: 1:27 PM
 */
public class AuthenticationService {

  @Nonnull
  private final SvnVcs myVcs;
  private final boolean myIsActive;
  private static final Logger LOG = Logger.getInstance(AuthenticationService.class);
  private File myTempDirectory;
  private boolean myProxyCredentialsWereReturned;
  private SvnConfiguration myConfiguration;
  private final Set<String> myRequestedCredentials;

  public AuthenticationService(@Nonnull SvnVcs vcs, boolean isActive) {
    myVcs = vcs;
    myIsActive = isActive;
    myConfiguration = SvnConfiguration.getInstance(myVcs.getProject());
    myRequestedCredentials = new HashSet<>();
  }

  @Nonnull
  public SvnVcs getVcs() {
    return myVcs;
  }

  @Nullable
  public File getTempDirectory() {
    return myTempDirectory;
  }

  public boolean isActive() {
    return myIsActive;
  }

  @Nullable
  public SVNAuthentication requestCredentials(final SVNURL repositoryUrl, final String type) {
    SVNAuthentication authentication = null;

    if (repositoryUrl != null) {
      final String realm = repositoryUrl.toDecodedString();

      authentication = requestCredentials(realm, type, new Getter<SVNAuthentication>() {
        @Override
        public SVNAuthentication get() {
          return myVcs.getSvnConfiguration().getInteractiveManager(myVcs).getInnerProvider()
            .requestClientAuthentication(type, repositoryUrl, realm, null, null, true);
        }
      });
    }

    if (authentication == null) {
      LOG.warn("Could not get authentication. Type - " + type + ", Url - " + repositoryUrl);
    }

    return authentication;
  }

  @Nullable
  private <T> T requestCredentials(@Nonnull String realm, @Nonnull String type, @Nonnull Getter<T> fromUserProvider) {
    T result = null;
    // Search for stored credentials not only by key but also by "parent" keys. This is useful when we work just with URLs
    // (not working copy) and can't detect repository url beforehand because authentication is required. If found credentials of "parent"
    // are not correct then current key will already be stored in myRequestedCredentials - thus user will be asked for credentials and
    // provided result will be stored in cache (with necessary key).
    Object data = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(type, realm);
    String key = SvnConfiguration.AuthStorage.getKey(type, realm);

    // we return credentials from cache if they are asked for the first time during command execution, otherwise - user is asked
    if (data != null && !myRequestedCredentials.contains(key)) {
      // we already have credentials in memory cache
      result = (T)data;
      myRequestedCredentials.add(key);
    }
    else if (myIsActive) {
      // ask user for credentials
      result = fromUserProvider.get();
      if (result != null) {
        // save user credentials to memory cache
        myVcs.getSvnConfiguration().acknowledge(type, realm, result);
        myRequestedCredentials.add(key);
      }
    }

    return result;
  }

  @Nullable
  public String requestSshCredentials(@Nonnull final String realm,
                                      @Nonnull final SimpleCredentialsDialog.Mode mode,
                                      @Nonnull final String key) {
    return requestCredentials(realm, StringUtil.toLowerCase(mode.toString()), new Getter<String>() {
      @Override
      public String get() {
        final Ref<String> answer = new Ref<>();

        Runnable command = new Runnable() {
          public void run() {
            SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myVcs.getProject());

            dialog.setup(mode, realm, key, true);
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
            dialog.setSaveEnabled(false);
            if (dialog.showAndGet()) {
              answer.set(dialog.getPassword());
            }
          }
        };

        // Use ModalityState.any() as currently ssh credentials in terminal mode are requested in the thread that reads output and not in
        // the thread that started progress
        WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(command, Application.get().getAnyModalityState());

        return answer.get();
      }
    });
  }

  @Nonnull
  public AcceptResult acceptCertificate(@Nonnull final SVNURL url, @Nonnull final String certificateInfo) {
    // TODO: Probably explicitly construct server url for realm here - like in CertificateTrustManager.
    String kind = "terminal.ssl.server";
    String realm = url.toDecodedString();
    Object data = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(kind, realm);
    AcceptResult result;

    if (data != null) {
      result = (AcceptResult)data;
    }
    else {
      result =
        AcceptResult.from(getAuthenticationManager().getInnerProvider().acceptServerAuthentication(url, realm, certificateInfo, true));

      if (!AcceptResult.REJECTED.equals(result)) {
        myVcs.getSvnConfiguration().acknowledge(kind, realm, result);
      }
    }

    return result;
  }

  public boolean acceptSSLServerCertificate(@Nullable SVNURL repositoryUrl, final String realm) throws SvnBindException {
    if (repositoryUrl == null) {
      return false;
    }

    boolean result;

    if (Registry.is("svn.use.svnkit.for.https.server.certificate.check")) {
      result = new SSLServerCertificateAuthenticator(this, repositoryUrl, realm).tryAuthenticate();
    }
    else {
      HttpClient client = getClient(repositoryUrl);

      try {
        client.execute(new HttpGet(repositoryUrl.toDecodedString()));
        result = true;
      }
      catch (IOException e) {
        throw new SvnBindException(fixMessage(e), e);
      }
    }

    return result;
  }

  @Nullable
  private static String fixMessage(@Nonnull IOException e) {
    String message = null;

    if (e instanceof SSLHandshakeException) {
      if (StringUtil.containsIgnoreCase(e.getMessage(), "received fatal alert: handshake_failure")) {
        message = e.getMessage() + ". Please try to specify SSL protocol manually - SSLv3 or TLSv1";
      }
      else if (e.getCause() != null) {
        // SSLHandshakeException.getMessage() could contain full type name of cause exception - for instance when cause is
        // CertificateException. We just use cause exception message not to show exception type to the user.
        message = e.getCause().getMessage();
      }
    }

    return message;
  }

  @Nonnull
  private HttpClient getClient(@Nonnull SVNURL repositoryUrl) {
    // TODO: Implement algorithm of resolving necessary enabled protocols (TLSv1 vs SSLv3) instead of just using values from Settings.
    SSLContext sslContext = createSslContext(repositoryUrl);
    List<String> supportedProtocols = getSupportedSslProtocols();
    SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext, ArrayUtil.toStringArray(supportedProtocols), null,
                                                                              SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    // TODO: Seems more suitable here to read timeout values directly from config file - without utilizing SvnAuthenticationManager.
    final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    if (haveDataForTmpConfig()) {
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyIfEnabled(requestConfigBuilder);
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyCredentialsIfEnabled(credentialsProvider);
    }

    return HttpClients.custom()
      .setSSLSocketFactory(socketFactory)
      .setDefaultSocketConfig(SocketConfig.custom()
                                .setSoTimeout(getAuthenticationManager().getReadTimeout(repositoryUrl))
                                .build())
      .setDefaultRequestConfig(requestConfigBuilder
                                 .setConnectTimeout(getAuthenticationManager().getConnectTimeout(repositoryUrl))
                                 .build())
      .setDefaultCredentialsProvider(credentialsProvider)
      .build();
  }

  @Nonnull
  private List<String> getSupportedSslProtocols() {
    List<String> result = ContainerUtil.newArrayList();

    switch (myConfiguration.getSslProtocols()) {
      case sslv3:
        result.add("SSLv3");
        break;
      case tlsv1:
        result.add("TLSv1");
        break;
      case all:
        break;
    }

    return result;
  }

  @Nonnull
  private SSLContext createSslContext(@Nonnull SVNURL url) {
    CertificateManager certificateManager = CertificateManager.getInstance();
    SSLContext result = certificateManager.getSystemSslContext();
    TrustManager trustManager = new CertificateTrustManager(this, url);

    try {
      result.init(certificateManager.getDefaultKeyManagers(), new TrustManager[]{trustManager}, null);
    }
    catch (KeyManagementException e) {
      LOG.error(e);
    }

    return result;
  }

  @Nonnull
  public SvnAuthenticationManager getAuthenticationManager() {
    return isActive() ? myConfiguration.getInteractiveManager(myVcs) : myConfiguration.getPassiveAuthenticationManager(myVcs.getProject());
  }

  public void clearPassiveCredentials(String realm, SVNURL repositoryUrl, boolean password) {
    if (repositoryUrl == null) {
      return;
    }

    final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
    final List<String> kinds = getKinds(repositoryUrl, password);

    for (String kind : kinds) {
      configuration.clearCredentials(kind, realm);
    }
  }

  // TODO: rename
  public boolean haveDataForTmpConfig() {
    final HttpProxyManager instance = HttpProxyManager.getInstance();
    return SvnConfiguration.getInstance(myVcs.getProject()).isIsUseDefaultProxy() &&
           (instance.isHttpProxyEnabled() || instance.isPacProxyEnabled());
  }

  @Nullable
  public static Proxy getIdeaDefinedProxy(@Nonnull final SVNURL url) {
    // SVNKit authentication implementation sets repositories as noProxy() to provide custom proxy authentication logic - see for instance,
    // SvnAuthenticationManager.getProxyManager(). But noProxy() setting is not cleared correctly in all cases - so if svn command
    // (for command line) is executed on thread where repository url was added as noProxy() => proxies are not retrieved for such commands
    // and execution logic is incorrect.

    // To prevent such behavior repositoryUrl is manually removed from noProxy() list (for current thread).
    // NOTE, that current method is only called from code flows for executing commands through command line client and should not be called
    // from SVNKit code flows.
    CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());

    HttpProxyManager httpProxyManager = HttpProxyManager.getInstance();
    final List<Proxy> proxies = CommonProxy.getInstance().select(URI.create(url.toString()));
    if (proxies != null && !proxies.isEmpty()) {
      for (Proxy proxy : proxies) {
        if (httpProxyManager.isRealProxy(proxy) && Proxy.Type.HTTP.equals(proxy.type())) {
          return proxy;
        }
      }
    }
    return null;
  }

  @Nullable
  public PasswordAuthentication getProxyAuthentication(@Nonnull SVNURL repositoryUrl) {
    Proxy proxy = getIdeaDefinedProxy(repositoryUrl);
    PasswordAuthentication result = null;

    if (proxy != null) {
      if (myProxyCredentialsWereReturned) {
        showFailedAuthenticateProxy();
      }
      else {
        result = getProxyAuthentication(proxy, repositoryUrl);
        myProxyCredentialsWereReturned = result != null;
      }
    }

    return result;
  }

  private static void showFailedAuthenticateProxy() {
    HttpProxyManager instance = HttpProxyManager.getInstance();
    String message = instance.isHttpProxyEnabled() || instance.isPacProxyEnabled()
                     ? "Failed to authenticate to proxy. You can change proxy credentials in HTTP proxy settings."
                     : "Failed to authenticate to proxy.";

    PopupUtil.showBalloonForActiveComponent(message, NotificationType.ERROR);
  }

  @Nullable
  private static PasswordAuthentication getProxyAuthentication(@Nonnull Proxy proxy, @Nonnull SVNURL repositoryUrl) {
    PasswordAuthentication result = null;

    try {
      result = Authenticator.requestPasswordAuthentication(repositoryUrl.getHost(), ((InetSocketAddress)proxy.address()).getAddress(),
                                                           repositoryUrl.getPort(), repositoryUrl.getProtocol(), repositoryUrl.getHost(),
                                                           repositoryUrl.getProtocol(), new URL(repositoryUrl.toString()),
                                                           Authenticator.RequestorType.PROXY);
    }
    catch (MalformedURLException e) {
      LOG.info(e);
    }

    return result;
  }

  public void reset() {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  @Nonnull
  public static List<String> getKinds(final SVNURL url, boolean passwordRequest) {
    if (passwordRequest || "http".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    }
    else if ("https".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.SSL);
    }
    else if ("svn".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    }
    else if (url.getProtocol().contains("svn+")) {  // todo +-
      return Arrays.asList(ISVNAuthenticationManager.SSH, ISVNAuthenticationManager.USERNAME);
    }
    return Collections.singletonList(ISVNAuthenticationManager.USERNAME);
  }

  @Nullable
  public File getSpecialConfigDir() {
    return myTempDirectory != null ? myTempDirectory : new File(myConfiguration.getConfigurationDirectory());
  }

  public void initTmpDir(SvnConfiguration configuration) throws IOException {
    if (myTempDirectory == null) {
      TempFileService tempFileService = Application.get().getInstance(TempFileService.class);

      myTempDirectory = tempFileService.createTempDirectory("tmp", "Subversion").toFile();
      FileUtil.copyDir(new File(configuration.getConfigurationDirectory()), myTempDirectory, FilePermissionCopier.BY_NIO2);
    }
  }
}
