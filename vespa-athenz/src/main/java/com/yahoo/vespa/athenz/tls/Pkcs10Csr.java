// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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

    public List<String> getSubjectAlternativeNames() {
        return getExtensions()
                .map(extensions -> GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName))
                .filter(Objects::nonNull)
                .flatMap(generalNames -> Arrays.stream(generalNames.getNames()))
                .map(Pkcs10Csr::toString)
                .collect(toList());
    }

    /**
     * @return If basic constraints extension is present: returns true if CA cert, false otherwise. Returns empty if the extension is not present.
     */
    public Optional<Boolean> getBasicConstraints() {
        return getExtensions()
                .map(BasicConstraints::fromExtensions)
                .findAny()
                .map(BasicConstraints::isCA);
    }

    public List<String> getExtensionOIds() {
        return getExtensions()
                .flatMap(extensions -> Arrays.stream(extensions.getExtensionOIDs()))
                .map(ASN1ObjectIdentifier::getId)
                .collect(toList());

    }

    private Stream<Extensions> getExtensions() {
        return Arrays
                .stream(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest))
                .map(attribute -> Extensions.getInstance(attribute.getAttrValues().getObjectAt(0)));
    }

    private static String toString(GeneralName generalName) {
        ASN1Encodable name = generalName.getName();
        switch (generalName.getTagNo()) {
            case GeneralName.rfc822Name:
            case GeneralName.dNSName:
            case GeneralName.uniformResourceIdentifier:
                return DERIA5String.getInstance(name).getString();
            case GeneralName.directoryName:
                return X500Name.getInstance(name).toString();
            default:
                return name.toString();
        }
    }
}
