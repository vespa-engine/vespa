// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;

/**
 * @author bjorncs
 */
public enum Extension {
    BASIC_CONSTRAINS(org.bouncycastle.asn1.x509.Extension.basicConstraints),
    SUBJECT_ALTERNATIVE_NAMES(org.bouncycastle.asn1.x509.Extension.subjectAlternativeName);

    final ASN1ObjectIdentifier extensionOId;

    Extension(ASN1ObjectIdentifier extensionOId) {
        this.extensionOId = extensionOId;
    }

    public String getOId() {
        return extensionOId.getId();
    }
}
