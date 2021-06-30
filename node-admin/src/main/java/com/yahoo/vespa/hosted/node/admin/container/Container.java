// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.config.provision.DockerImage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Podman container.
 *
 * @author mpolden
 */
public class Container extends PartialContainer {

    private final String hostname;
    private final ContainerResources resources;
    private final int conmonPid;
    private final List<Network> networks;

    public Container(ContainerId id, ContainerName name, State state, String imageId, DockerImage image,
                     Map<String, String> labels, int pid, int conmonPid, String hostname,
                     ContainerResources resources, List<Network> networks, boolean managed) {
        super(id, name, state, imageId, image, labels, pid, managed);
        this.hostname = Objects.requireNonNull(hostname);
        this.resources = Objects.requireNonNull(resources);
        this.conmonPid = conmonPid;
        this.networks = List.copyOf(Objects.requireNonNull(networks));
    }

    /** The hostname of this, if any */
    public String hostname() {
        return hostname;
    }

    /** Resource limits for this*/
    public ContainerResources resources() {
        return resources;
    }

    /** Pid of the conmon process for this container */
    public int conmonPid() {
        return conmonPid;
    }

    /** The networks used by this */
    public List<Network> networks() {
        return networks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Container that = (Container) o;
        return conmonPid == that.conmonPid && hostname.equals(that.hostname) && resources.equals(that.resources) && networks.equals(that.networks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), hostname, resources, conmonPid, networks);
    }

    /** The network of a container */
    public static class Network {

        private final String name;
        private final String ipv4Address;

        public Network(String name, String ipv4Address) {
            this.name = Objects.requireNonNull(name);
            this.ipv4Address = Objects.requireNonNull(ipv4Address);
        }

        public String name() {
            return name;
        }

        public String ipv4Address() {
            return ipv4Address;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Network network = (Network) o;
            return name.equals(network.name) && ipv4Address.equals(network.ipv4Address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, ipv4Address);
        }

    }

}
