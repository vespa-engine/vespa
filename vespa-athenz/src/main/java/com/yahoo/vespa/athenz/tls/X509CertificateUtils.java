// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class X509CertificateUtils {

    private X509CertificateUtils() {}

    public static X509Certificate fromPem(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object pemObject = parser.readObject();
            if (pemObject instanceof X509Certificate) {
                return (X509Certificate) pemObject;
            }
            if (pemObject instanceof X509CertificateHolder) {
                return new JcaX509CertificateConverter()
                        .setProvider(BouncyCastleProviderHolder.getInstance())
                        .getCertificate((X509CertificateHolder) pemObject);
            }
            throw new IllegalArgumentException("Invalid type of PEM object: " + pemObject);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toPem(X509Certificate certificate) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
