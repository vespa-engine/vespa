// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerList;
import com.yahoo.vespa.hosted.provision.node.filter.ApplicationFilter;

import java.util.List;
import java.util.Optional;

/**
 * @author mpolden
 */
public class LoadBalancersResponse extends SlimeJsonResponse {

    private final NodeRepository nodeRepository;
    private final HttpRequest request;

    public LoadBalancersResponse(HttpRequest request, NodeRepository nodeRepository) {
        this.request = request;
        this.nodeRepository = nodeRepository;
        Cursor root = slime.setObject();
        toSlime(loadBalancers(), root);
    }

    private Optional<ApplicationId> application() {
        return Optional.ofNullable(request.getProperty("application"))
                       .map(ApplicationFilter::toApplicationId);
    }

    private List<LoadBalancer> loadBalancers() {
        LoadBalancerList loadBalancers;
        var application = application();
        if (application.isPresent()) {
            loadBalancers = nodeRepository.loadBalancers().list(application.get());
        } else {
            loadBalancers = nodeRepository.loadBalancers().list();
        }
        return loadBalancers.asList();
    }

    private void toSlime(List<LoadBalancer> loadBalancers, Cursor object) {
        Cursor loadBalancerArray = object.setArray("loadBalancers");
        loadBalancers.forEach(lb -> {
            Cursor lbObject = loadBalancerArray.addObject();
            lbObject.setString("id", lb.id().serializedForm());
            lbObject.setString("state", lb.state().name());
            lbObject.setLong("changedAt", lb.changedAt().toEpochMilli());
            lbObject.setString("application", lb.id().application().application().value());
            lbObject.setString("tenant", lb.id().application().tenant().value());
            lbObject.setString("instance", lb.id().application().instance().value());
            lbObject.setString("cluster", lb.id().cluster().value());
            lb.instance().flatMap(LoadBalancerInstance::hostname).ifPresent(hostname -> lbObject.setString("hostname", hostname.value()));
            lb.instance().flatMap(LoadBalancerInstance::ipAddress).ifPresent(ipAddress -> lbObject.setString("ipAddress", ipAddress));
            lb.instance().flatMap(LoadBalancerInstance::dnsZone).ifPresent(dnsZone -> lbObject.setString("dnsZone", dnsZone.id()));

            Cursor networkArray = lbObject.setArray("networks");
            lb.instance().ifPresent(instance -> instance.networks().forEach(networkArray::addString));

            Cursor portArray = lbObject.setArray("ports");
            lb.instance().ifPresent(instance -> instance.ports().forEach(portArray::addLong));

            Cursor realArray = lbObject.setArray("reals");
            lb.instance().ifPresent(instance -> {
                instance.reals().forEach(real -> {
                    Cursor realObject = realArray.addObject();
                    realObject.setString("hostname", real.hostname().value());
                    realObject.setString("ipAddress", real.ipAddress());
                    realObject.setLong("port", real.port());
                });
            });
            lb.instance().ifPresent(instance -> {
                if ( ! instance.settings().isDefault()) {
                    Cursor urnsArray = lbObject.setObject("settings").setArray("allowedUrns");
                    for (AllowedUrn urn : instance.settings().allowedUrns()) {
                        Cursor urnObject = urnsArray.addObject();
                        urnObject.setString("type", switch (urn.type()) {
                                                        case awsPrivateLink -> "aws-private-link";
                                                        case gcpServiceConnect -> "gcp-service-connect";
                                                    });
                        urnObject.setString("urn", urn.urn());
                    }
                }
                instance.serviceId().ifPresent(serviceId -> lbObject.setString("serviceId", serviceId.value()));
                lbObject.setBool("public", instance.settings().isPublicEndpoint());
            });
            lb.instance()
              .map(LoadBalancerInstance::cloudAccount)
              .filter(cloudAccount -> !cloudAccount.isUnspecified())
              .ifPresent(cloudAccount -> lbObject.setString("cloudAccount", cloudAccount.value()));
        });
    }

}
