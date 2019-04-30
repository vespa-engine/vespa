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

    private static final String rsaPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                 "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsKL8jvIEy2peLtEvyhWW\n" +
                                                 "b/O/9RHTfPXjeXahXmVrXE4zY5CJ6Mf1PFkwQ8K8S35YhSbOZM4aYhF9V8F4jwyW\n" +
                                                 "nX6qWUMrWVHOuS32fkjdNo0z/KxCbG5nRIWLuv/PkHNuIJqMCbwn6Qud5a+wxeLg\n" +
                                                 "LqlroCtUJKAGj4YlZ5i8oMdCqfHKl/DMwcks5XxtIArz6GcM2z8fOB3NRexj32MU\n" +
                                                 "LH7ybWhCDx/RSqGQYJ8sWEFIK4HSmYqwqIQpFAm/ixISkeWBL6ikgqchZNMf7xyn\n" +
                                                 "yJxjCHgtkxANsQhHj2kgAzLDeBsuM+/WRhBGa+LRvEcuu/zZv9+7eVhpaYJveLVd\n" +
                                                 "cwPewW/8liBmKIzj/QPCn7ZlVRk094TZD6TCER4+JFW9mo0vFD8S9o0zhMlckzCF\n" +
                                                 "4ZNNgyP9tI8Wecq25A+sUY5/WZNLi+mka/GnfPt97GrhM0YHb1M6t4nh1R437Nwh\n" +
                                                 "rUHR/YDazbBvLk5T71GgfQfn44L9SwsqEYaHvdZAfV0IZJBtDo/yCe/yvgtHTymB\n" +
                                                 "eBrRMpBU5recPtW8bgEWlHl6Qyduw9EBJjNYxvBpgV/D/tNBcau0aGxmhwpBevet\n" +
                                                 "ekV6XA2miC7rWu2Wrq2l5LjXEgZOD5PNN2vQS2Cdet9JHYWbVbK3mBLgoChcC5Xo\n" +
                                                 "/QHLU4RydI0i0+Z2/tjGsGsCAwEAAQ==\n" +
                                                 "-----END PUBLIC KEY-----\n";

    private static final String ecPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                "-----END PUBLIC KEY-----\n";

    private static final String ecPemPrivateKey = "-----BEGIN EC PRIVATE KEY-----\n" +
                                                  "MHcCAQEEIJUmbIX8YFLHtpRgkwqDDE3igU9RG6JD9cYHWAZii9j7oAoGCCqGSM49\n" +
                                                  "AwEHoUQDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9z/4jKSTHwbYR8wdsOSrJGVEU\n" +
                                                  "PbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                  "-----END EC PRIVATE KEY-----\n";

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
    }

    @Test
    public void can_serialize_and_deserialize_ec_privatekey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        String pem = KeyUtils.toPem(keyPair.getPrivate());
        assertThat(pem, containsString("BEGIN EC PRIVATE KEY"));
        assertThat(pem, containsString("END EC PRIVATE KEY"));
        PrivateKey deserializedKey = KeyUtils.fromPemEncodedPrivateKey(pem);
        assertEquals(keyPair.getPrivate(), deserializedKey);
    }

    @Test
    public void can_deserialize_rsa_publickey_in_pem_format() {
        KeyUtils.fromPemEncodedPublicKey(rsaPemPublicKey);
    }

    @Test
    public void can_deserialize_ec_keys_in_pem_format() {
        KeyUtils.fromPemEncodedPublicKey(ecPemPublicKey);
        KeyUtils.fromPemEncodedPrivateKey(ecPemPrivateKey);
    }

}
