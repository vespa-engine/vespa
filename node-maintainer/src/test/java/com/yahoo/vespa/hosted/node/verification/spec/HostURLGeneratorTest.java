package com.yahoo.vespa.hosted.node.verification.spec;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Created by olaa on 14/07/2017.
 */
public class HostURLGeneratorTest {

    @Test
    public void generateNodeInfoUrl_test_if_url_is_formatted_correctly() throws Exception {
        String zoneHostName = "http://cfg1.prod.us-west-1.vespahosted.gq1.yahoo.com:4080";
        String midUrl = "/nodes/v2/node/";
        String nodeHostName = "13305821.ostk.bm2.prod.gq1.yahoo.com";
        HostURLGenerator hostURLGenerator = spy(new HostURLGenerator());
        when(hostURLGenerator.getEnvironmentVariable("HOSTNAME")).thenReturn(nodeHostName);
        URL url = hostURLGenerator.generateNodeInfoUrl(zoneHostName);
        String expectedUrl = zoneHostName + midUrl + nodeHostName;
        String actualUrl = url.toString();
        assertEquals(expectedUrl, actualUrl);
    }

}