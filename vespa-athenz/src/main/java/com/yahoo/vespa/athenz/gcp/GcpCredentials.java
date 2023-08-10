package com.yahoo.vespa.athenz.gcp;

import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.ExternalAccountCredentials;
import com.yahoo.security.token.TokenDomain;
import com.yahoo.security.token.TokenGenerator;
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
import java.util.Objects;

public class GcpCredentials {
    private static final TokenDomain domain = TokenDomain.of("athenz-gcp-oauth2-nonce");

    final private InputStream tokenApiStream;
    private final HttpTransportFactory httpTransportFactory;

    private GcpCredentials(Builder builder) {
        String clientId = builder.athenzDomain.getName() + ".gcp";
        String audience = String.format("//iam.googleapis.com/projects/%s/locations/global/workloadIdentityPools/%s/providers/%s",
                builder.projectNumber, builder.workloadPoolName, builder.workloadProviderName);
        String serviceUrl = String.format("https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/%s@%s.iam.gserviceaccount.com:generateAccessToken",
                builder.serviceAccountName, builder.projectName);
        String scope = URLEncoder.encode(generateIdTokenScope(builder.athenzDomain.getName(), builder.role), StandardCharsets.UTF_8);
        String redirectUri = URLEncoder.encode(generateRedirectUri(clientId, builder.redirectURISuffix), StandardCharsets.UTF_8);
        String tokenUrl = String.format("%s/oauth2/auth?response_type=id_token&client_id=%s&redirect_uri=%s&scope=%s&nonce=%s&keyType=EC&fullArn=true&output=json",
                builder.ztsUrl, clientId, redirectUri, scope, TokenGenerator.generateToken(domain, "", 32).secretTokenString());

        tokenApiStream = createTokenAPIStream(audience, serviceUrl, tokenUrl, builder.tokenLifetimeSeconds);
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(builder.identityProvider.getIdentitySslContext());
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

    private static String generateIdTokenScope(final String domainName, String roleName) {
        StringBuilder scope = new StringBuilder(256);
        scope.append("openid");
        scope.append(' ').append(domainName).append(":role.").append(roleName);
        return scope.toString();
    }

    private static String generateRedirectUri(final String clientId, String uriSuffix) {
        int idx = clientId.lastIndexOf('.');
        if (idx == -1) {
            return "";
        }
        final String dashDomain = clientId.substring(0, idx).replace('.', '-');
        final String service = clientId.substring(idx + 1);
        return "https://" + service + "." + dashDomain + "." + uriSuffix;
    }


    public static class Builder {
        private String ztsUrl;
        private ServiceIdentityProvider identityProvider;
        private String redirectURISuffix;
        private AthenzDomain athenzDomain;
        private String role;
        private String projectName;
        private String projectNumber;
        private String serviceAccountName;

        private int tokenLifetimeSeconds = 3600; // default to 1 hour lifetime
        private String workloadPoolName = "athenz";
        private String workloadProviderName = "athenz";

        public GcpCredentials build() {
            Objects.requireNonNull(ztsUrl);
            Objects.requireNonNull(identityProvider);
            Objects.requireNonNull(redirectURISuffix);
            Objects.requireNonNull(athenzDomain);
            Objects.requireNonNull(role);
            Objects.requireNonNull(projectName);
            Objects.requireNonNull(projectNumber);
            Objects.requireNonNull(serviceAccountName);

            return new GcpCredentials(this);
        }

        public Builder setZtsUrl(String ztsUrl) {
            this.ztsUrl = ztsUrl;
            return this;
        }

        public Builder identityProvider(ServiceIdentityProvider provider) {
            this.identityProvider = provider;
            return this;
        }

        public Builder redirectURISuffix(String redirectURISuffix) {
            this.redirectURISuffix = redirectURISuffix;
            return this;
        }

        public Builder athenzDomain(AthenzDomain athenzDomain) {
            this.athenzDomain = athenzDomain;
            return this;
        }

        public Builder role(String gcpRole) {
            this.role = gcpRole;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder projectNumber(String projectNumber) {
            this.projectNumber = projectNumber;
            return this;
        }

        public Builder serviceAccountName(String serviceAccountName) {
            this.serviceAccountName = serviceAccountName;
            return this;
        }

        public Builder tokenLifetimeSeconds(int tokenLifetimeSeconds) {
            this.tokenLifetimeSeconds = tokenLifetimeSeconds;
            return this;
        }

        public Builder workloadPoolName(String workloadPoolName) {
            this.workloadPoolName = workloadPoolName;
            return this;
        }

        public Builder workloadProviderName(String workloadProviderName) {
            this.workloadProviderName = workloadProviderName;
            return this;
        }
    }
}
