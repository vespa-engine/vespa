package com.yahoo.container.jdisc.athenz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.Beta;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.impl.AthenzService;
import com.yahoo.container.jdisc.athenz.impl.InstanceIdentity;
import com.yahoo.container.jdisc.athenz.impl.InstanceRegisterInformation;
import com.yahoo.container.jdisc.athenz.impl.ServiceProviderApi;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * @author mortent
 */
@Beta
// TODO Separate out into interface and "hidden" implementation
public final class AthenzIdentityProvider extends AbstractComponent {

    private InstanceIdentity instanceIdentity;

    private final String dnsSuffix;
    private final String providerUniqueId;

    @Inject
    public AthenzIdentityProvider(IdentityConfig config, ConfigserverConfig configserverConfig) throws IOException {
        this(config, new ServiceProviderApi(configserverConfig.loadBalancerAddress()), new AthenzService());
    }

    // Test only
    public AthenzIdentityProvider(IdentityConfig config, ServiceProviderApi serviceProviderApi, AthenzService athenzService) throws IOException {
        KeyPair keyPair = createKeyPair();
        String signedIdentityDocument = serviceProviderApi.getSignedIdentityDocument();
        String athenzUrl = getZtsEndpoint(signedIdentityDocument);
        dnsSuffix = getDnsSuffix(signedIdentityDocument);
        providerUniqueId = getProviderUniqueId(signedIdentityDocument);
        String providerServiceName = getProviderServiceName(signedIdentityDocument);

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

    private static String getProviderUniqueId(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "provider-unique-id");
    }

    private static String getDnsSuffix(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "dns-suffix");
    }

    private static String getProviderServiceName(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "provider-service");
    }

    private static String getZtsEndpoint(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "zts-endpoint");
    }

    private static String getJsonNode(String jsonString, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonString);
        return jsonNode.get(path).asText();
    }

    private static KeyPair createKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String createCSR(KeyPair keyPair, IdentityConfig identityConfig) throws IOException {

        try {
            // Add SAN dnsname <service>.<domain-with-dashes>.<provider-dnsname-suffix>
            // and SAN dnsname <provider-unique-instance-id>.instanceid.athenz.<provider-dnsname-suffix>
            GeneralNames subjectAltNames = new GeneralNames(new GeneralName[]{
                    new GeneralName(GeneralName.dNSName, String.format("%s.%s.%s",
                                                                          identityConfig.serviceName(),
                                                                          identityConfig.domain().replace(".", "-"),
                                                                          dnsSuffix)),
                    new GeneralName(GeneralName.dNSName, String.format("%s.instanceid.athenz.%s",
                                                                       providerUniqueId,
                                                                       dnsSuffix))
            });

            ExtensionsGenerator extGen = new ExtensionsGenerator();
            extGen.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

            X500Principal subject = new X500Principal(
                    String.format("CN=%s.%s", identityConfig.domain(), identityConfig.serviceName()));

            PKCS10CertificationRequestBuilder requestBuilder =
                    new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
            requestBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());
            PKCS10CertificationRequest csr = requestBuilder.build(
                    new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate()));

            PemObject pemObject = new PemObject("CERTIFICATE REQUEST", csr.getEncoded());
            try (StringWriter stringWriter = new StringWriter()) {
                try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
                    pemWriter.writeObject(pemObject);
                    return stringWriter.toString();
                }
            }
        } catch (OperatorCreationException e) {
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

