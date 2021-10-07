// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Collectors;

public class OperatorCertificateSerializer {

    private final static String certificateField = "certificates";


    public static Slime toSlime(List<X509Certificate> certificateList) {
        Slime slime = new Slime();
        var root = slime.setObject();
        Cursor array = root.setArray(certificateField);
        certificateList.stream()
                .map(X509CertificateUtils::toPem)
                .forEach(array::addString);
        return slime;
    }

    public static List<X509Certificate> fromSlime(Inspector object) {
        return SlimeUtils.entriesStream(object.field(certificateField))
                .map(Inspector::asString)
                .map(X509CertificateUtils::fromPem)
                .collect(Collectors.toList());
    }
}
