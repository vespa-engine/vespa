// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class KeyUtilsTest {

    @Test
    public void can_extract_public_key_from_rsa_private() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        PublicKey publicKey = KeyUtils.extractPublicKey(keyPair.getPrivate());
        assertNotNull(publicKey);
    }

    @Test
    public void can_extract_public_key_from_ecdsa_private() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        PublicKey publicKey = KeyUtils.extractPublicKey(keyPair.getPrivate());
        assertNotNull(publicKey);
    }

    @Test
    public void can_serialize_and_deserialize_rsa_privatekey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        String pem = KeyUtils.toPem(keyPair.getPrivate());
        assertThat(pem, containsString("BEGIN RSA PRIVATE KEY"));
        assertThat(pem, containsString("END RSA PRIVATE KEY"));
        PrivateKey deserializedKey = KeyUtils.fromPemEncodedPrivateKey(pem);
        assertEquals(keyPair.getPrivate(), deserializedKey);
        assertEquals(KeyAlgorithm.RSA.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

    @Test
    public void can_serialize_and_deserialize_ec_privatekey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        String pem = KeyUtils.toPem(keyPair.getPrivate());
        assertThat(pem, containsString("BEGIN EC PRIVATE KEY"));
        assertThat(pem, containsString("END EC PRIVATE KEY"));
        PrivateKey deserializedKey = KeyUtils.fromPemEncodedPrivateKey(pem);
        assertEquals(keyPair.getPrivate(), deserializedKey);
        assertEquals(KeyAlgorithm.EC.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

    @Test
    public void can_serialize_and_deserialize_rsa_publickey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        String pem = KeyUtils.toPem(keyPair.getPublic());
        assertThat(pem, containsString("BEGIN PUBLIC KEY"));
        assertThat(pem, containsString("END PUBLIC KEY"));
        PublicKey deserializedKey = KeyUtils.fromPemEncodedPublicKey(pem);
        assertEquals(keyPair.getPublic(), deserializedKey);
        assertEquals(KeyAlgorithm.RSA.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

    @Test
    public void can_serialize_and_deserialize_ec_publickey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        String pem = KeyUtils.toPem(keyPair.getPublic());
        assertThat(pem, containsString("BEGIN PUBLIC KEY"));
        assertThat(pem, containsString("END PUBLIC KEY"));
        PublicKey deserializedKey = KeyUtils.fromPemEncodedPublicKey(pem);
        assertEquals(keyPair.getPublic(), deserializedKey);
        assertEquals(KeyAlgorithm.EC.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

}
