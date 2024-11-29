package com.yahoo.config.provision.athenz;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hakon
 */
public class AthenzTest {
    @Test
    void verifyUris() {
        assertEquals("https://zts.athenz.cd.vespa-cloud.com:4443/zts/v1", Athenz.VESPA_CD.ztsUri().toString());
        assertEquals("https://zms.athenz.cd.vespa-cloud.com:4443/zms/v1", Athenz.VESPA_CD.zmsUri().toString());

        assertEquals("https://zts.athenz.vespa-cloud.com:4443/zts/v1", Athenz.VESPA.ztsUri().toString());
        assertEquals("https://zms.athenz.vespa-cloud.com:4443/zms/v1", Athenz.VESPA.zmsUri().toString());

        assertEquals("https://zts.athenz.ouroath.com:4443/zts/v1", Athenz.YAHOO.ztsUri().toString());
        assertEquals("https://zms.athenz.ouroath.com:4443/zms/v1", Athenz.YAHOO.zmsUri().toString());

        assertEquals("https://zts.athens.yahoo.com:4443/zts/v1", Athenz.YAHOO_ONPREM.ztsUri().toString());
        assertEquals("https://zms.athens.yahoo.com:4443/zms/v1", Athenz.YAHOO_ONPREM.zmsUri().toString());
    }
}
