package com.yahoo.security.tls;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * @author bjorncs
 */
public class TransportSecurityOptionsTest {

    private static final Path TEST_CONFIG_FILE = Paths.get("src/test/resources/transport-security-options.json");

    @Test
    public void can_read_options_from_json_file() {
        TransportSecurityOptions expectedOptions = new TransportSecurityOptions("myhost.key", "certs.pem", "my_cas.pem");
        TransportSecurityOptions actualOptions =  TransportSecurityOptions.fromJsonFile(TEST_CONFIG_FILE);
        assertEquals(expectedOptions, actualOptions);
    }

}