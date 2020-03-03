// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.application.validation.AccessControlValidatorTestBase;
import org.junit.Before;

/**
 * @author gjoranv
 */
public class AccessControlOnFirstDeploymentValidatorTest extends AccessControlValidatorTestBase {

    @Before
    public void setup() {
        validator = new AccessControlOnFirstDeploymentValidator();
        zone = new Zone(Environment.prod, RegionName.from("foo"));
    }

}
