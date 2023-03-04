// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.security.Pkcs10CsrUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.ClusterType;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.vespa.hosted.ca.instance.InstanceRefresh;
import com.yahoo.vespa.hosted.ca.instance.InstanceRegistration;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mpolden
 */
public class InstanceSerializer {

    private static final String PROVIDER_FIELD = "provider";
    private static final String DOMAIN_FIELD = "domain";
    private static final String SERVICE_FIELD = "service";
    private static final String ATTESTATION_DATA_FIELD = "attestationData";
    private static final String CSR_FIELD = "csr";
    private static final String NAME_FIELD = "service";
    private static final String INSTANCE_ID_FIELD = "instanceId";
    private static final String X509_CERTIFICATE_FIELD = "x509Certificate";

    private static final String IDD_SIGNATURE_FIELD = "signature";
    private static final String IDD_SIGNING_KEY_VERSION_FIELD = "signing-key-version";
    private static final String IDD_PROVIDER_UNIQUE_ID_FIELD = "provider-unique-id";
    private static final String IDD_PROVIDER_SERVICE_FIELD = "provider-service";
    private static final String IDD_DOCUMENT_VERSION_FIELD = "document-version";
    private static final String IDD_CONFIGSERVER_HOSTNAME_FIELD = "configserver-hostname";
    private static final String IDD_INSTANCE_HOSTNAME_FIELD = "instance-hostname";
    private static final String IDD_CREATED_AT_FIELD = "created-at";
    private static final String IDD_IPADDRESSES_FIELD = "ip-addresses";
    private static final String IDD_IDENTITY_TYPE_FIELD = "identity-type";
    private static final String IDD_CLUSTER_TYPE_FIELD = "cluster-type";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    static {
        objectMapper.registerModule(new JavaTimeModule());
    }

    private InstanceSerializer() {}

    public static InstanceRegistration registrationFromSlime(Slime slime) {
        Cursor root = slime.get();
        return new InstanceRegistration(requireField(PROVIDER_FIELD, root).asString(),
                                        requireField(DOMAIN_FIELD, root).asString(),
                                        requireField(SERVICE_FIELD, root).asString(),
                                        attestationDataToIdentityDocument(StringUtilities.unescape(requireField(ATTESTATION_DATA_FIELD, root).asString())),
                                        Pkcs10CsrUtils.fromPem(requireField(CSR_FIELD, root).asString()));
    }

    public static InstanceRefresh refreshFromSlime(Slime slime) {
        Cursor root = slime.get();
        return new InstanceRefresh(Pkcs10CsrUtils.fromPem(requireField(CSR_FIELD, root).asString()));
    }

    public static Slime identityToSlime(InstanceIdentity identity) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(PROVIDER_FIELD, identity.provider());
        root.setString(NAME_FIELD, identity.service());
        root.setString(INSTANCE_ID_FIELD, identity.instanceId());
        identity.x509Certificate()
                .map(X509CertificateUtils::toPem)
                .ifPresent(pem -> root.setString(X509_CERTIFICATE_FIELD, pem));
        return slime;
    }

    public static SignedIdentityDocument attestationDataToIdentityDocument(String attestationData) {
        Slime slime = SlimeUtils.jsonToSlime(attestationData);
        Cursor root = slime.get();
        String signature = requireField(IDD_SIGNATURE_FIELD, root).asString();
        long signingKeyVersion = requireField(IDD_SIGNING_KEY_VERSION_FIELD, root).asLong();
        VespaUniqueInstanceId providerUniqueId = VespaUniqueInstanceId.fromDottedString(requireField(IDD_PROVIDER_UNIQUE_ID_FIELD, root).asString());
        AthenzService athenzService = new AthenzService(requireField(IDD_PROVIDER_SERVICE_FIELD, root).asString());
        long documentVersion = requireField(IDD_DOCUMENT_VERSION_FIELD, root).asLong();
        String configserverHostname = requireField(IDD_CONFIGSERVER_HOSTNAME_FIELD, root).asString();
        String instanceHostname = requireField(IDD_INSTANCE_HOSTNAME_FIELD, root).asString();
        double createdAtTimestamp = requireField(IDD_CREATED_AT_FIELD, root).asDouble();
        Instant createdAt = getJsr310Instant(createdAtTimestamp);
        Set<String> ips = new HashSet<>();
        requireField(IDD_IPADDRESSES_FIELD, root).traverse((ArrayTraverser)  (__, entry) -> ips.add(entry.asString()));
        IdentityType identityType = IdentityType.fromId(requireField(IDD_IDENTITY_TYPE_FIELD, root).asString());
        var clusterTypeField = root.field(IDD_CLUSTER_TYPE_FIELD);
        var clusterType = clusterTypeField.valid() ? ClusterType.from(clusterTypeField.asString()) : null;


        return new SignedIdentityDocument(signature, (int)signingKeyVersion, providerUniqueId, athenzService, (int)documentVersion,
                                          configserverHostname, instanceHostname, createdAt, ips, identityType, clusterType);
    }

    private static Instant getJsr310Instant(double v) {
        try {
            return objectMapper.readValue(Double.toString(v), Instant.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Cursor requireField(String fieldName, Cursor root) {
        var field = root.field(fieldName);
        if (!field.valid()) throw new IllegalArgumentException("Missing required field '" + fieldName + "'");
        return field;
    }

}
