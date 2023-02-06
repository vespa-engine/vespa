package com.yahoo.vespa.hosted.node.admin.wireguard;

import com.yahoo.config.provision.WireguardKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class TenantParametersTest {

    private static final String dummyKey = "qT+1Kdx7qZZpbqBxHupj7XgmVXSfcXol1RccaSd40XA=";

    @Test
    public void parameters_can_be_converted_to_json_and_back() {
        TenantParameters params = new TenantParameters("host", WireguardKey.from(dummyKey));
        TenantParameters params2 = TenantParameters.fromJson(params.toJson());
        assertEquals(params, params2);
    }

}
