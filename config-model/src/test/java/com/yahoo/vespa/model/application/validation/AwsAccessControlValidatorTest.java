package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import org.junit.Before;

import static com.yahoo.vespa.model.application.validation.AwsAccessControlValidator.AWS_CLOUD_NAME;

/**
 * @author gjoranv
 */
public class AwsAccessControlValidatorTest extends AccessControlValidatorTestBase {

    @Before
    public void setup() {
        validator = new AwsAccessControlValidator();
        zone = new Zone(CloudName.from(AWS_CLOUD_NAME), SystemName.main, Environment.prod, RegionName.from("foo"));
    }

}
