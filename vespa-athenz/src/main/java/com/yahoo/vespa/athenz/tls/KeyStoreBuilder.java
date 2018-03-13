// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * @author bjorncs
 */
public class KeyStoreBuilder {

    private final List<KeyEntry> keyEntries = new ArrayList<>();
    private final List<CertificateEntry> certificateEntries = new ArrayList<>();

    private final KeyStoreType keyStoreType;
    private File inputFile;
    private char[] inputFilePassword;

    private KeyStoreBuilder(KeyStoreType keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public static KeyStoreBuilder withType(KeyStoreType type) {
        return new KeyStoreBuilder(type);
    }

    public KeyStoreBuilder fromFile(File file, char[] password) {
        this.inputFile = file;
        this.inputFilePassword = password;
        return this;
    }

    public KeyStoreBuilder fromFile(File file) {
        return fromFile(file, null);
    }

    public KeyStoreBuilder withKeyEntry(String alias, PrivateKey privateKey, char[] password, List<X509Certificate> certificateChain) {
        keyEntries.add(new KeyEntry(alias, privateKey, certificateChain, password));
        return this;
    }

    public KeyStoreBuilder withKeyEntry(String alias, PrivateKey privateKey, char[] password, X509Certificate certificate) {
        return withKeyEntry(alias, privateKey, password, singletonList(certificate));
    }

    public KeyStoreBuilder withKeyEntry(String alias, PrivateKey privateKey, X509Certificate certificate) {
        return withKeyEntry(alias, privateKey, null, certificate);
    }

    public KeyStoreBuilder withKeyEntry(String alias, PrivateKey privateKey, List<X509Certificate> certificateChain) {
        return withKeyEntry(alias, privateKey, null, certificateChain);
    }

    public KeyStoreBuilder withCertificateEntry(String alias, X509Certificate certificate) {
        certificateEntries.add(new CertificateEntry(alias, certificate));
        return this;
    }

    public KeyStore build() {
        try {
            KeyStore keystore = this.keyStoreType.createKeystore();
            if (this.inputFile != null) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(this.inputFile))) {
                    keystore.load(in, this.inputFilePassword);
                }
            } else {
                keystore.load(null);
            }
            for (KeyEntry entry : keyEntries) {
                char[] password = entry.password != null ? entry.password : new char[0];
                Certificate[] certificateChain = entry.certificateChain.toArray(new Certificate[entry.certificateChain.size()]);
                keystore.setKeyEntry(entry.alias, entry.privateKey, password, certificateChain);
            }
            for (CertificateEntry entry : certificateEntries) {
                keystore.setCertificateEntry(entry.alias, entry.certificate);
            }
            return keystore;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class KeyEntry {
        final String alias;
        final PrivateKey privateKey;
        final List<X509Certificate> certificateChain;
        final char[] password;

        KeyEntry(String alias, PrivateKey privateKey, List<X509Certificate> certificateChain, char[] password) {
            this.alias = alias;
            this.privateKey = privateKey;
            this.certificateChain = certificateChain;
            this.password = password;
        }
    }

    private static class CertificateEntry {
        final String alias;
        final X509Certificate certificate;

        CertificateEntry(String alias, X509Certificate certificate) {
            this.alias = alias;
            this.certificate = certificate;
        }
    }
}
