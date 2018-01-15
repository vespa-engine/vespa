package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class AthenzUtilsTest {

    @Test
    public void athenz_identity_is_parsed_from_dot_separated_string() {
        AthenzIdentity expectedIdentity = new AthenzService(new AthenzDomain("my.subdomain"), "myservicename");
        String fullName = expectedIdentity.getFullName();
        AthenzIdentity actualIdentity = AthenzUtils.createAthenzIdentity(fullName);
        assertEquals(expectedIdentity, actualIdentity);
    }

}