package com.yahoo.vespa.hosted.node.admin.wireguard;

import com.yahoo.config.provision.zone.ZoneApi;

import java.util.List;

/**
 * A cloud-agnostic store of parameters for Wireguard.
 *
 * @author gjoranv
 */
public interface ParameterStore {

    /** Returns the configservers for the given zone. */
    List<ConfigserverParameters> getConfigservers(ZoneApi zoneApi);

    /** Returns the tenant nodes for the given zone. */
    List<TenantParameters> getTenantNodes(ZoneApi zoneApi);

    void addConfigserver(ConfigserverParameters configserver);

    void addTenantNode(TenantParameters tenant);

    void removeConfigserver(String hostname);

    void removeTenantNode(String hostname);

}
