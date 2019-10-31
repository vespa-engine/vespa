// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.security.Pkcs10CsrUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.vespa.hosted.ca.instance.InstanceRefresh;
import com.yahoo.vespa.hosted.ca.instance.InstanceRegistration;

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

    private InstanceSerializer() {}

    public static InstanceRegistration registrationFromSlime(Slime slime) {
        Cursor root = slime.get();
        return new InstanceRegistration(requireField(PROVIDER_FIELD, root).asString(),
                                        requireField(DOMAIN_FIELD, root).asString(),
                                        requireField(SERVICE_FIELD, root).asString(),
                                        requireField(ATTESTATION_DATA_FIELD, root).asString(),
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

    private static Cursor requireField(String fieldName, Cursor root) {
        var field = root.field(fieldName);
        if (!field.valid()) throw new IllegalArgumentException("Missing required field '" + fieldName + "'");
        return field;
    }

}
