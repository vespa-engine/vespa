// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author bjorncs
 */
public class Pkcs10CsrUtils {

    private Pkcs10CsrUtils() {}

    public static Pkcs10Csr fromPem(String pem) {
        try (PEMParser pemParser = new PEMParser(new StringReader(pem))) {
            return new Pkcs10Csr((PKCS10CertificationRequest) pemParser.readObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toPem(Pkcs10Csr csr) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE REQUEST", csr.getBcCsr().getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns all DNS names contained in given CSR (CN + subject alternative names) */
    public static List<String> dnsNames(Pkcs10Csr csr) {
        return Stream.concat(X509CertificateUtils.getCommonNames(csr.getSubject()).stream(),
                             csr.getSubjectAlternativeNames().stream()
                                .map(SubjectAlternativeName::getValue))
                     .toList();
    }

}
