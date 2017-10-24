// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.pem;

import com.google.common.base.Preconditions;
import com.yahoo.jdisc.http.ssl.ReaderForPath;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import static com.yahoo.jdisc.http.server.jetty.Exceptions.throwUnchecked;

/**
 * Exposes keys and certificates from unencrypted PEM keystore.
 *
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class PemKeyStore extends KeyStoreSpi {

    private static String KEY_ALIAS = "KEY";

    static List<String> aliases = Collections.emptyList();
    static Map<String, String> attributes = Collections.emptyMap();
    private static final BouncyCastleProvider bouncyCastleProvider = new BouncyCastleProvider();

    @GuardedBy("this")
    private StoreRole storeRole;
    @GuardedBy("this")
    private Key privateKey;
    @GuardedBy("this")
    private final Map<String, Certificate> aliasToCertificate = new LinkedHashMap<>();


    public PemKeyStore() {}


    /**
     * The user is responsible for closing any readers given in the parameter.
     */
    @Override
    public synchronized void engineLoad(LoadStoreParameter parameter) throws IOException {
        if (storeRole != null)
            throw new IllegalStateException("Already initialized.");

        if (parameter instanceof KeyStoreLoadParameter) {
            storeRole = new KeyStoreRole();
            loadKeyStore((KeyStoreLoadParameter) parameter);
        } else if (parameter instanceof TrustStoreLoadParameter) {
            storeRole = new TrustStoreRole();
            loadTrustStore((TrustStoreLoadParameter) parameter);
        } else {
            throw new IllegalArgumentException("Expected key store or trust store load parameter, got " + parameter.getClass());
        }
    }

    private void loadTrustStore(TrustStoreLoadParameter parameter) throws IOException {
        withPemParser(parameter.certificateReader, this::loadCertificates);
    }

    private void loadKeyStore(KeyStoreLoadParameter parameter) throws IOException{
        withPemParser(parameter.keyReader, this::loadPrivateKey);
        withPemParser(parameter.certificateReader,  this::loadCertificates);
    }

    private static void withPemParser(ReaderForPath reader, Consumer<PEMParser> f) throws IOException {
        try {
            //parser.close() will close the underlying reader,
            //which we want to avoid.
            //See engineLoad comment.
            PEMParser parser = new PEMParser(reader.reader);
            f.accept(parser);
        } catch (Exception e) {
            throw new RuntimeException("Failed loading pem key store " + reader.path, e);
        }
    }

    private void loadPrivateKey(PEMParser parser) {
        try {
            Object object = parser.readObject();
            PrivateKeyInfo privateKeyInfo;
            if (object instanceof PEMKeyPair) { // Legacy PKCS1
                privateKeyInfo = ((PEMKeyPair) object).getPrivateKeyInfo();
            } else if (object instanceof PrivateKeyInfo) { // PKCS8
                privateKeyInfo = (PrivateKeyInfo) object;
            } else {
                throw new UnsupportedOperationException(
                        "Expected " + PrivateKeyInfo.class + " or " + PEMKeyPair.class + ", got " + object.getClass());
            }

            Object nextObject = parser.readObject();
            if (nextObject != null) {
                throw new UnsupportedOperationException(
                        "Expected a single private key, but found a second element " + nextObject.getClass());
            }

            setPrivateKey(privateKeyInfo);
        } catch (Exception e) {
            throw throwUnchecked(e);
        }
    }

    private synchronized void setPrivateKey(PrivateKeyInfo privateKey) throws PEMException {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(bouncyCastleProvider);
        this.privateKey = converter.getPrivateKey(privateKey);
    }

    private void loadCertificates(PEMParser parser) {
        try {
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                addCertificate(pemObject);
            }

            if (aliasToCertificate.isEmpty())
                throw new RuntimeException("No certificates available");
        } catch (Exception e) {
            throw throwUnchecked(e);
        }
    }

    private synchronized void addCertificate(Object pemObject) throws CertificateException {
        if (pemObject instanceof X509CertificateHolder) {
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider(bouncyCastleProvider);
            String alias = "cert-" + aliasToCertificate.size();
            aliasToCertificate.put(alias, converter.getCertificate((X509CertificateHolder) pemObject));
        } else {
            throw new UnsupportedOperationException("Expected X509 certificate, got " + pemObject.getClass());
        }
    }

    @Override
    public synchronized Enumeration<String> engineAliases() {
        return Collections.enumeration(storeRole.engineAliases());

    }

    @Override
    public synchronized boolean engineIsKeyEntry(String alias) {
        return KEY_ALIAS.equals(alias);
    }

    @Override
    public synchronized Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        Preconditions.checkArgument(KEY_ALIAS.equals(alias));
        return privateKey;
    }

    @Override
    public synchronized boolean engineIsCertificateEntry(String alias) {
        return aliasToCertificate.containsKey(alias);
    }


    @Override
    public synchronized Certificate engineGetCertificate(String alias) {
        return aliasToCertificate.get(alias);
    }

    @Override
    public synchronized Certificate[] engineGetCertificateChain(String alias) {
        Preconditions.checkArgument(KEY_ALIAS.equals(alias));
        return aliasToCertificate.values().toArray(new Certificate[aliasToCertificate.size()]);
    }


    @Override
    public synchronized boolean engineContainsAlias(String alias) {
        return storeRole.engineContainsAlias(alias);
    }

    @Override
    public synchronized int engineSize() {
        return storeRole.engineSize();
    }

    @Override
    public synchronized String engineGetCertificateAlias(final Certificate certificate) {
        for (Entry<String, Certificate> entry : aliasToCertificate.entrySet()) {
            if (entry.getValue() == certificate)
                return entry.getKey();
        }

        return null;
    }

    @Override
    public synchronized Date engineGetCreationDate(String alias) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }


    @Override
    public synchronized void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
    }

    private interface StoreRole {
        Collection<String> engineAliases();
        boolean engineContainsAlias(String alias);
        int engineSize();
    }

    private class KeyStoreRole implements StoreRole {
        @Override
        public Collection<String> engineAliases() {
            return Collections.singletonList(KEY_ALIAS);
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return KEY_ALIAS.equals(alias);
        }

        @Override
        public int engineSize() {
            return 1;
        }
    }

    private class TrustStoreRole implements StoreRole{
        @Override
        public Collection<String> engineAliases() {
            return aliasToCertificate.keySet();
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return aliasToCertificate.containsKey(alias);
        }

        @Override
        public int engineSize() {
            return aliasToCertificate.size();
        }
    }

    public static class PemLoadStoreParameter implements LoadStoreParameter {
        private PemLoadStoreParameter() {}

        @Override
        public ProtectionParameter getProtectionParameter() {
            return null;
        }
    }

    public static final class KeyStoreLoadParameter extends PemLoadStoreParameter {
        public final ReaderForPath certificateReader;
        public final ReaderForPath keyReader;

        public KeyStoreLoadParameter(ReaderForPath certificateReader, ReaderForPath keyReader) {
            this.certificateReader = certificateReader;
            this.keyReader = keyReader;
        }
    }

    public static final class TrustStoreLoadParameter extends PemLoadStoreParameter {
        public final ReaderForPath certificateReader;

        public TrustStoreLoadParameter(ReaderForPath certificateReader) {
            this.certificateReader = certificateReader;
        }
    }
}
