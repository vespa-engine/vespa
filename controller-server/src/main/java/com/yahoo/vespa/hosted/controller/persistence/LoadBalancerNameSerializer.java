// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.loadbalancer.LoadBalancerName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializer and deserializer for LoadBalancerName
 *
 * @author mortent
 */
public class LoadBalancerNameSerializer {

    private static final String loadBalancerNamesField = "loadBalancerNames";

    public Slime toSlime(Map<ApplicationId, List<LoadBalancerName>> loadBalancerNames) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor applicationEndpoints = root.setObject(loadBalancerNamesField);

        for (Map.Entry<ApplicationId, List<LoadBalancerName>> entry : loadBalancerNames.entrySet()) {
            ApplicationId applicationId = entry.getKey();
            Cursor cursor = applicationEndpoints.setArray(applicationId.serializedForm());
            entry.getValue().forEach( lb -> cursor.addObject().setString(lb.recordId().asString(), lb.recordName().asString()));
        }
        return slime;
    }

    public Map<ApplicationId, List<LoadBalancerName>> fromSlime(Slime slime) {

        Inspector object = slime.get().field(loadBalancerNamesField);
        Map<ApplicationId, List<LoadBalancerName>> loadBalancerNames = new HashMap<>();
        object.traverse((ObjectTraverser) (appId, inspector) ->
                loadBalancerNames.put(ApplicationId.fromSerializedForm(appId), loadBalancerNamesFromSlime(inspector)));

        return loadBalancerNames;
    }

    private List<LoadBalancerName> loadBalancerNamesFromSlime(Inspector root) {
        List<LoadBalancerName> names = new ArrayList<>();
        root.traverse((ArrayTraverser) (i, inspector) -> inspector.traverse((ObjectTraverser)(x,y)-> names.add(new LoadBalancerName(new RecordId(x), RecordName.from(y.asString())))));
        return names;
    }
}
