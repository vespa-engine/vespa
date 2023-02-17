// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.lb.DnsZone;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.PrivateServiceId;
import com.yahoo.vespa.hosted.provision.lb.Real;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Serializer for load balancers.
 *
 * @author mpolden
 */
public class LoadBalancerSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String idField = "id";
    private static final String hostnameField = "hostname";
    private static final String lbIpAddressField = "ipAddress";
    private static final String stateField = "state";
    private static final String changedAtField = "changedAt";
    private static final String dnsZoneField = "dnsZone";
    private static final String portsField = "ports";
    private static final String networksField = "networks";
    private static final String realsField = "reals";
    private static final String ipAddressField = "ipAddress";
    private static final String portField = "port";
    private static final String serviceIdField = "serviceId";
    private static final String serviceIdsField = "serviceIds";
    private static final String cloudAccountField = "cloudAccount";
    private static final String settingsField = "settings";
    private static final String publicField = "public";
    private static final String privateField = "private";
    private static final String allowedUrnsField = "allowedUrns";
    private static final String accessTypeField = "type";
    private static final String urnField = "urn";

    public static byte[] toJson(LoadBalancer loadBalancer) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString(idField, loadBalancer.id().serializedForm());
        loadBalancer.instance().flatMap(LoadBalancerInstance::hostname).ifPresent(hostname -> root.setString(hostnameField, hostname.value()));
        loadBalancer.instance().flatMap(LoadBalancerInstance::ipAddress).ifPresent(ip -> root.setString(lbIpAddressField, ip));
        root.setString(stateField, asString(loadBalancer.state()));
        root.setLong(changedAtField, loadBalancer.changedAt().toEpochMilli());
        loadBalancer.instance().flatMap(LoadBalancerInstance::dnsZone).ifPresent(dnsZone -> root.setString(dnsZoneField, dnsZone.id()));
        Cursor portArray = root.setArray(portsField);
        loadBalancer.instance().ifPresent(instance -> instance.ports().forEach(portArray::addLong));
        Cursor networkArray = root.setArray(networksField);
        loadBalancer.instance().ifPresent(instance -> instance.networks().forEach(networkArray::addString));
        Cursor realArray = root.setArray(realsField);
        loadBalancer.instance().ifPresent(instance -> instance.reals().forEach(real -> {
            Cursor realObject = realArray.addObject();
            realObject.setString(hostnameField, real.hostname().value());
            realObject.setString(ipAddressField, real.ipAddress());
            realObject.setLong(portField, real.port());
        }));
        loadBalancer.instance()
                    .map(LoadBalancerInstance::settings)
                    .ifPresent(settings -> toSlime(root.setObject(settingsField), settings));
        loadBalancer.instance()
                    .flatMap(LoadBalancerInstance::serviceId)
                    .ifPresent(serviceId -> root.setString(serviceIdField, serviceId.value())); // TODO: remove after winter vacation '23
        loadBalancer.instance().stream()
                    .map(LoadBalancerInstance::serviceIds).flatMap(List::stream)
                    .map(PrivateServiceId::value)
                    .forEach(root.setArray(serviceIdsField)::addString);
        loadBalancer.instance()
                    .map(LoadBalancerInstance::cloudAccount)
                    .filter(cloudAccount -> ! cloudAccount.isUnspecified())
                    .ifPresent(cloudAccount -> root.setString(cloudAccountField, cloudAccount.value()));
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LoadBalancer fromJson(byte[] data) {
        Cursor object = SlimeUtils.jsonToSlime(data).get();

        Set<Real> reals = new LinkedHashSet<>();
        object.field(realsField).traverse((ArrayTraverser) (i, realObject) -> {
            reals.add(new Real(DomainName.of(realObject.field(hostnameField).asString()),
                               realObject.field(ipAddressField).asString(),
                               (int) realObject.field(portField).asLong()));

        });

        Set<Integer> ports = new LinkedHashSet<>();
        object.field(portsField).traverse((ArrayTraverser) (i, port) -> ports.add((int) port.asLong()));

        Set<String> networks = new LinkedHashSet<>();
        object.field(networksField).traverse((ArrayTraverser) (i, network) -> networks.add(network.asString()));

        Optional<DomainName> hostname = optionalString(object.field(hostnameField), Function.identity()).filter(s -> !s.isEmpty()).map(DomainName::of);
        Optional<String> ipAddress = optionalString(object.field(lbIpAddressField), Function.identity()).filter(s -> !s.isEmpty());
        Optional<DnsZone> dnsZone = optionalString(object.field(dnsZoneField), DnsZone::new);
        ZoneEndpoint settings = zoneEndpoint(object.field(settingsField));
        Optional<PrivateServiceId> serviceId = optionalString(object.field(serviceIdField), PrivateServiceId::of);
        List<PrivateServiceId> serviceIds = new ArrayList<>();
        object.field(serviceIdsField).traverse((ArrayTraverser) (__, serviceIdObject) -> serviceIds.add(PrivateServiceId.of(serviceIdObject.asString())));
        if (serviceIds.isEmpty()) serviceId.ifPresent(serviceIds::add); // TODO: remove after winter vacation '23
        CloudAccount cloudAccount = optionalString(object.field(cloudAccountField), CloudAccount::from).orElse(CloudAccount.empty);
        Optional<LoadBalancerInstance> instance = hostname.isEmpty() && ipAddress.isEmpty()
                                                  ? Optional.empty()
                                                  : Optional.of(new LoadBalancerInstance(hostname, ipAddress, dnsZone, ports, networks, reals, settings, serviceIds, cloudAccount));

        return new LoadBalancer(LoadBalancerId.fromSerializedForm(object.field(idField).asString()),
                                instance,
                                stateFromString(object.field(stateField).asString()),
                                Instant.ofEpochMilli(object.field(changedAtField).asLong()));
    }

    private static void toSlime(Cursor settingsObject, ZoneEndpoint settings) {
        settingsObject.setBool(publicField, settings.isPublicEndpoint());
        settingsObject.setBool(privateField, settings.isPrivateEndpoint());
        if (settings.isPrivateEndpoint()) {
            Cursor allowedUrnsArray = settingsObject.setArray(allowedUrnsField);
            for (AllowedUrn urn : settings.allowedUrns()) {
                Cursor urnObject = allowedUrnsArray.addObject();
                urnObject.setString(urnField, urn.urn());
                urnObject.setString(accessTypeField,
                                    switch (urn.type()) {
                                        case awsPrivateLink -> "awsPrivateLink";
                                        case gcpServiceConnect -> "gcpServiceConnect";
                                    });
            }
        }
    }

    private static ZoneEndpoint zoneEndpoint(Inspector settingsObject) {
        if ( ! settingsObject.valid()) return ZoneEndpoint.defaultEndpoint;
        return new ZoneEndpoint(settingsObject.field(publicField).asBool(),
                                settingsObject.field(privateField).asBool(),
                                SlimeUtils.entriesStream(settingsObject.field(allowedUrnsField))
                                          .map(urnObject -> new AllowedUrn(switch (urnObject.field(accessTypeField).asString()) {
                                                                               case "awsPrivateLink" -> AccessType.awsPrivateLink;
                                                                               case "gcpServiceConnect" -> AccessType.gcpServiceConnect;
                                                                               default -> throw new IllegalArgumentException("unknown service access type in '" + urnObject + "'");
                                                                           },
                                                                           urnObject.field(urnField).asString()))
                                          .toList());
    }

    private static <T> Optional<T> optionalValue(Inspector field, Function<Inspector, T> fieldMapper) {
        return Optional.of(field).filter(Inspector::valid).map(fieldMapper);
    }

    private static <T> Optional<T> optionalString(Inspector field, Function<String, T> fieldMapper) {
        return optionalValue(field, Inspector::asString).map(fieldMapper);
    }

    private static String asString(LoadBalancer.State state) {
        return switch (state) {
            case active -> "active";
            case inactive -> "inactive";
            case reserved -> "reserved";
            case removable -> "removable";
        };
    }

    private static LoadBalancer.State stateFromString(String state) {
        return switch (state) {
            case "active" -> LoadBalancer.State.active;
            case "inactive" -> LoadBalancer.State.inactive;
            case "reserved" -> LoadBalancer.State.reserved;
            case "removable" -> LoadBalancer.State.removable;
            default -> throw new IllegalArgumentException("No serialization defined for state string '" + state + "'");
        };
    }

}
