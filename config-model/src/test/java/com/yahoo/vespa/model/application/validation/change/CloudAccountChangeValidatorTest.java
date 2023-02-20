package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.IntRange;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author mpolden
 */
class CloudAccountChangeValidatorTest {

    @Test
    public void validate() {
        VespaModel model0 = model(provisioned(capacity(CloudAccount.empty)));
        VespaModel model1 = model(provisioned(capacity(CloudAccount.from("000000000000"))));

        CloudAccountChangeValidator validator = new CloudAccountChangeValidator();
        try {
            validator.validate(model0, model1, new DeployState.Builder().build());
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Cannot change cloud account from unspecified account to " +
                                         "account '000000000000'. The existing deployment must be removed before " +
                                         "changing accounts");
        }
        assertEquals(List.of(), validator.validate(model0, model0, new DeployState.Builder().build()));
        assertEquals(List.of(), validator.validate(model1, model1, new DeployState.Builder().build()));
    }

    private static Provisioned provisioned(Capacity... capacity) {
        Provisioned provisioned = new Provisioned();
        for (int i = 0; i < capacity.length; i++) {
            provisioned.add(ClusterSpec.Id.from("c" + i), capacity[i]);
        }
        return provisioned;
    }

    private static Capacity capacity(CloudAccount cloudAccount) {
        NodeResources nodeResources = new NodeResources(4, 8, 100, 10);
        return Capacity.from(new ClusterResources(2, 1, nodeResources),
                             new ClusterResources(2, 1, nodeResources),
                             IntRange.empty(),
                             false,
                             false,
                             Optional.of(cloudAccount).filter(account -> !account.isUnspecified()));
    }

    private static VespaModel model(Provisioned provisioned) {
        var properties = new TestProperties();
        properties.setHostedVespa(true);
        var deployState = new DeployState.Builder().properties(properties)
                                                   .provisioned(provisioned);
        String services = """
                <?xml version='1.0' encoding='utf-8' ?>
                <services version='1.0'>
                  <container id='c0' version='1.0'>
                    <nodes count='2'>
                      <resources vcpu='4' memory='8Gb' disk='100Gb'/>
                    </nodes>
                  </container>
                  <content id='c1' version='1.0'>
                    <nodes count='2'>
                      <resources vcpu='4' memory='8Gb' disk='100Gb'/>
                    </nodes>
                    <documents>
                      <document type='test' mode='index'/>
                    </documents>
                    <redundancy>2</redundancy>
                  </content>
                </services>""";
        return new VespaModelCreatorWithMockPkg(null, services, List.of("schema test { document test {} }"))
                .create(deployState);
    }

}
