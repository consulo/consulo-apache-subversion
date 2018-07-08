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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.net.ssl.ClientOnlyTrustManager;
import org.apache.http.client.utils.URIBuilder;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.svn.SvnConfiguration;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * We assume that this trust manager is only used when server certificate is valid but untrusted. So we do not perform any additional
 * validation here - just checking if certificate is trusted in several ways:
 * - runtime cache
 * - java trust store
 * - "Server Certificates" settings
 * - ask user
 *
 * @author Konstantin Kolosovsky.
 */
public class CertificateTrustManager extends ClientOnlyTrustManager {

  private static final Logger LOG = Logger.getInstance(CertificateTrustManager.class);

  @Nonnull
  private final AuthenticationService myAuthenticationService;
  @Nonnull
  private final SVNURL myRepositoryUrl;
  @Nonnull
  private final String myRealm;

  public CertificateTrustManager(@Nonnull AuthenticationService authenticationService, @Nonnull SVNURL repositoryUrl) {
    myAuthenticationService = authenticationService;
    myRepositoryUrl = repositoryUrl;
    myRealm = new URIBuilder()
      .setScheme(repositoryUrl.getProtocol())
      .setHost(repositoryUrl.getHost())
      .setPort(repositoryUrl.getPort())
      .toString();
  }

  @Override
  public void checkServerTrusted(@Nullable X509Certificate[] chain, String authType) throws CertificateException {
    if (chain != null && chain.length > 0 && chain[0] != null) {
      X509Certificate certificate = chain[0];

      if (!checkPassive(certificate)) {
        if (!isAcceptedByIdea(chain, authType)) {
          checkActive(certificate);
        }

        // no exceptions - so certificate is trusted - save to runtime cache
        acknowledge(certificate);
      }
    }
  }

  private boolean checkPassive(@Nonnull X509Certificate certificate) throws CertificateEncodingException {
    Object cachedData = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck("svn.ssl.server", myRealm);

    return certificate.equals(cachedData);
  }

  private static boolean isAcceptedByIdea(@Nonnull X509Certificate[] chain, String authType) {
    boolean result;

    try {
      CertificateManager.getInstance().getTrustManager().checkServerTrusted(chain, authType, false, false);
      result = true;
    }
    catch (CertificateException e) {
      LOG.debug(e);
      result = false;
    }

    return result;
  }

  private void checkActive(@Nonnull X509Certificate certificate) throws CertificateException {
    boolean isStorageEnabled =
      myAuthenticationService.getAuthenticationManager().getHostOptionsProvider().getHostOptions(myRepositoryUrl).isAuthStorageEnabled();
    int result = myAuthenticationService.getAuthenticationManager().getInnerProvider()
      .acceptServerAuthentication(myRepositoryUrl, myRealm, certificate, isStorageEnabled);

    switch (result) {
      case ISVNAuthenticationProvider.ACCEPTED:
        // TODO: --trust-server-cert command line key does not allow caching credentials permanently - so permanent caching should be
        // TODO: separately implemented. Try utilizing "Server Certificates" settings for this.
      case ISVNAuthenticationProvider.ACCEPTED_TEMPORARY:
        // acknowledge() is called in checkServerTrusted()
        break;
      case ISVNAuthenticationProvider.REJECTED:
        throw new CertificateException("Server SSL certificate rejected");
    }
  }

  private void acknowledge(@Nonnull X509Certificate certificate) throws CertificateEncodingException {
    myAuthenticationService.getVcs().getSvnConfiguration().acknowledge("cmd.ssl.server", myRealm, certificate);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return CertificateManager.getInstance().getTrustManager().getAcceptedIssuers();
  }
}
