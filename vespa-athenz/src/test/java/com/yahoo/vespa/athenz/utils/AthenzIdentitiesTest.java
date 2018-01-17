package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class AthenzIdentitiesTest {

    @Test
    public void athenz_identity_is_parsed_from_dot_separated_string() {
        AthenzIdentity expectedIdentity = new AthenzService(new AthenzDomain("my.subdomain"), "myservicename");
        String fullName = expectedIdentity.getFullName();
        AthenzIdentity actualIdentity = AthenzIdentities.from(fullName);
        assertEquals(expectedIdentity, actualIdentity);
    }

}