package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class LoadBalancerEndpointTest {

    @Test
    public void create() {
        Random random = new Random(3);
        ClusterSpec.Id cluster = ClusterSpec.Id.from("c0");
        ApplicationId application = ApplicationId.from("foo", "bar", "baz");
        LoadBalancerId loadBalancer = new LoadBalancerId(application, cluster);
        LoadBalancerEndpoint loadBalancerEndpoint = LoadBalancerEndpoint.create(loadBalancer, Environment.prod, RegionName.from("no-north-1"),
                                                                                LoadBalancerEndpoint.AuthMethod.mtls, random);
        assertEquals("c75f4427", loadBalancerEndpoint.id());
    }

}
