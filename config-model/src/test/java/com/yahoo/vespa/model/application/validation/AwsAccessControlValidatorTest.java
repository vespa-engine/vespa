// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import org.junit.Before;

/**
 * @author gjoranv
 */
public class AwsAccessControlValidatorTest extends AccessControlValidatorTestBase {

    @Before
    public void setup() {
        validator = new AwsAccessControlValidator();
        zone = new Zone(Cloud.builder().requireAccessControl(true).build(),
                        SystemName.main, Environment.prod, RegionName.from("foo"));
    }

}
