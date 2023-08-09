package com.yahoo.vespa.athenz.gcp;

import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.ExternalAccountCredentials;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GcpCredentials {
    final private static String WORKLOAD_POOL_NAME = "athenz";
    final private static String WORKLOAD_PROVIDER_NAME = "athenz";

    final private InputStream tokenApiStream;
    private final HttpTransportFactory httpTransportFactory;

    public GcpCredentials(String ztsUrl, ServiceIdentityProvider provider, String redirectURISuffix, int tokenLifetimeSeconds, AthenzDomain athenzDomain, String gcpRole, String projectName, String projectNumber, String serviceAccountName) {
        String clientId = athenzDomain.getName() + ".gcp";
        final String audience = String.format("//iam.googleapis.com/projects/%s/locations/global/workloadIdentityPools/%s/providers/%s",
                projectNumber, WORKLOAD_POOL_NAME, WORKLOAD_PROVIDER_NAME);
        final String serviceUrl = String.format("https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/%s@%s.iam.gserviceaccount.com:generateAccessToken",
                serviceAccountName, projectName);
        final String scope = URLEncoder.encode(generateIdTokenScope(athenzDomain.getName(), gcpRole), StandardCharsets.UTF_8);
        final String redirectUri = URLEncoder.encode(generateRedirectUri(clientId, redirectURISuffix), StandardCharsets.UTF_8);
        final String tokenUrl = String.format("%s/oauth2/auth?response_type=id_token&client_id=%s&redirect_uri=%s&scope=%s&nonce=%s&keyType=EC&fullArn=true&output=json",
                ztsUrl, clientId, redirectUri, scope, Crypto.randomSalt());

        tokenApiStream = createTokenAPIStream(audience, serviceUrl, tokenUrl, tokenLifetimeSeconds);
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(provider.getIdentitySslContext());
        HttpClientBuilder httpClientBuilder = ApacheHttpTransport.newDefaultHttpClientBuilder()
                .setSSLSocketFactory(sslConnectionSocketFactory);
        httpTransportFactory = () -> new ApacheHttpTransport(httpClientBuilder.build());
    }

    public ExternalAccountCredentials getCredential() throws IOException {
        return ExternalAccountCredentials.fromStream(tokenApiStream, httpTransportFactory);
    }

    private InputStream createTokenAPIStream(final String audience, final String serviceUrl, final String tokenUrl,
                                     int tokenLifetimeSeconds) {

        Slime root = new Slime();
        Cursor c = root.setObject();

        c.setString("type", "external_account");
        c.setString("audience", audience);
        c.setString("subject_token_type", "urn:ietf:params:oauth:token-type:jwt");
        c.setString("token_url", "https://sts.googleapis.com/v1/token");

        c.setString("service_account_impersonation_url", serviceUrl);
        Cursor sai = c.setObject("service_account_impersonation");
        sai.setLong("token_lifetime_seconds", tokenLifetimeSeconds);

        Cursor credentialSource = c.setObject("credential_source");
        credentialSource.setString("url", tokenUrl);

        Cursor credentialSourceFormat = credentialSource.setObject("format");
        credentialSourceFormat.setString("type", "json");
        credentialSourceFormat.setString("subject_token_field_name", "id_token");

        try {
            return new ByteArrayInputStream(SlimeUtils.toJsonBytes(root));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateIdTokenScope(final String domainName, String roleName) {
        StringBuilder scope = new StringBuilder(256);
        scope.append("openid");
        scope.append(' ').append(domainName).append(":role.").append(roleName);
        return scope.toString();
    }

    public static String generateRedirectUri(final String clientId, String uriSuffix) {
        int idx = clientId.lastIndexOf('.');
        if (idx == -1) {
            return "";
        }
        final String dashDomain = clientId.substring(0, idx).replace('.', '-');
        final String service = clientId.substring(idx + 1);
        return "https://" + service + "." + dashDomain + "." + uriSuffix;
    }

}
