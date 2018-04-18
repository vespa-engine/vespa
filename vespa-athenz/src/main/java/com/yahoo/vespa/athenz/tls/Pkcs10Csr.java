// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class Pkcs10Csr {

    private final PKCS10CertificationRequest csr;

    Pkcs10Csr(PKCS10CertificationRequest csr) {
        this.csr = csr;
    }

    PKCS10CertificationRequest getBcCsr() {
        return csr;
    }

    public X500Principal getSubject() {
        return new X500Principal(csr.getSubject().toString());
    }

    public List<SubjectAlternativeName> getSubjectAlternativeNames() {
        return getExtensions()
                .map(extensions -> GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName))
                .map(SubjectAlternativeName::fromGeneralNames)
                .orElse(emptyList());
    }

    /**
     * @return If basic constraints extension is present: returns true if CA cert, false otherwise. Returns empty if the extension is not present.
     */
    public Optional<Boolean> getBasicConstraints() {
        return getExtensions()
                .map(BasicConstraints::fromExtensions)
                .map(BasicConstraints::isCA);
    }

    public List<String> getExtensionOIds() {
        return getExtensions()
                .map(extensions -> Arrays.stream(extensions.getExtensionOIDs())
                        .map(ASN1ObjectIdentifier::getId)
                        .collect(toList()))
                .orElse(emptyList());

    }

    private Optional<Extensions> getExtensions() {
        return Optional.of(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest))
                .filter(attributes -> attributes.length > 0)
                .map(attributes -> attributes[0])
                .map(attribute -> Extensions.getInstance(attribute.getAttrValues().getObjectAt(0)));
    }

}
