package com.yahoo.container.jdisc.athenz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * @author mortent
 */
public final class AthenzIdentityProvider extends AbstractComponent {

    private InstanceIdentity instanceIdentity;

    private final String athenzUrl;

    private final String dnsSuffix;
    private final String providerUniqueId;
    private final String providerServiceName;

    @Inject
    public AthenzIdentityProvider(IdentityConfig config, ConfigserverConfig configserverConfig) throws IOException {
        this(config, new ServiceProviderApi(configserverConfig.loadBalancerAddress()), new AthenzService());
    }

    // Test only
    public AthenzIdentityProvider(IdentityConfig config, ServiceProviderApi serviceProviderApi, AthenzService athenzService) throws IOException {
        KeyPair keyPair = createKeyPair();
        String signedIdentityDocument = serviceProviderApi.getSignedIdentityDocument();
        this.athenzUrl = getZtsEndpoint(signedIdentityDocument);
        dnsSuffix = getDnsSuffix(signedIdentityDocument);
        providerUniqueId = getProviderUniqueId(signedIdentityDocument);
        providerServiceName = getProviderServiceName(signedIdentityDocument);

        InstanceRegisterInformation instanceRegisterInformation = new InstanceRegisterInformation(
                providerServiceName,
                config.domain(),
                config.serviceName(),
                signedIdentityDocument,
                createCSR(keyPair, config),
                true
        );
        instanceIdentity = athenzService.sendInstanceRegisterRequest(instanceRegisterInformation, athenzUrl);
    }

    private String getProviderUniqueId(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "provider-unique-id");
    }

    private String getDnsSuffix(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "dns-suffix");
    }

    private String getProviderServiceName(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "provider-service");
    }

    private String getZtsEndpoint(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "zts-endpoint");
    }

    private String getJsonNode(String jsonString, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonString);
        return jsonNode.get(path).asText();
    }

    private KeyPair createKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String createCSR(KeyPair keyPair, IdentityConfig identityConfig) throws IOException {

        try {
            // Add SAN dnsname <service>.<domain-with-dashes>.<provider-dnsname-suffix>
            // and SAN dnsname <provider-unique-instance-id>.instanceid.athenz.<provider-dnsname-suffix>
            GeneralName[] sanDnsNames = new GeneralName[]{
                    new GeneralName(GeneralName.dNSName, String.format("%s.%s.%s",
                                                                          identityConfig.serviceName(),
                                                                          identityConfig.domain().replace(".", "-"),
                                                                          dnsSuffix)),
                    new GeneralName(GeneralName.dNSName, String.format("%s.instanceid.athenz.%s",
                                                                       providerUniqueId,
                                                                       dnsSuffix))
            };

            return Crypto.generateX509CSR(keyPair.getPrivate(),
                                          keyPair.getPublic(),
                                          String.format("CN=%s.%s", identityConfig.domain(), identityConfig.serviceName()),
                                          sanDnsNames);
        } catch (OperatorCreationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public String getNToken() {
        return instanceIdentity.getServiceToken();
    }

    public String getX509Cert() {
        return instanceIdentity.getX509Certificate();
    }
}

