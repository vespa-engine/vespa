// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.vespa.config.ConfigPayload;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 * @author vegardh
 */
public class ConfigInstanceSerializationTest {

    @Test
    public void require_symmetrical_serialization_and_deserialization_with_builder() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();

        // NOTE: configId must be ':parent:' because the library replaces ReferenceNodes with that value with
        //        the instance's configId. (And the config used here contains such nodes.)
        List<String> lines = ConfigInstance.serialize(config);
        ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(lines);

        FunctionTestConfig config2 = ConfigInstanceUtil.getNewInstance(FunctionTestConfig.class, ":parent:", payload);
        assertEquals(config, config2);
        assertEquals(ConfigInstance.serialize(config), ConfigInstance.serialize(config2));
    }

}
