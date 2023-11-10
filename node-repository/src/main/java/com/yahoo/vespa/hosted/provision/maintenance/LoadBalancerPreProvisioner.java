package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.provisioning.LoadBalancerProvisioner;

import java.time.Duration;

/**
 * @author jonmv
 */
public class LoadBalancerPreProvisioner extends NodeRepositoryMaintainer {

    private final LoadBalancerProvisioner provisioner;

    public LoadBalancerPreProvisioner(NodeRepository nodeRepository, Duration interval, LoadBalancerService service, Metric metric) {
        super(nodeRepository, interval, metric);
        this.provisioner = new LoadBalancerProvisioner(nodeRepository, service);
    }

    @Override
    protected double maintain() {
        provisioner.refreshPool();
        return 0;
    }

}
