// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.loadtypes;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class keeps track of all configured load types.
 *
 * For production use, you should only use the String constructor,
 * and supply a valid config id. Only the load types configured will
 * be propagated throughout the system, so there is no point in using other
 * load types.
 *
 * For testing, you may want to use the empty constructor and add
 * load types yourself with addType().
 */
public class LoadTypeSet {

    class DualMap {
        Map<String, LoadType> nameMap = new TreeMap<String, LoadType>();
        Map<Integer, LoadType> idMap = new HashMap<Integer, LoadType>();

        void put(LoadType l) {
            if (nameMap.containsKey(l.getName()) || idMap.containsKey(l.getId())) {
                throw new IllegalArgumentException(
                    "ID or name conflict when adding " + l);
            }

            nameMap.put(l.getName(), l);
            idMap.put(l.getId(), l);
        }
    }

    DualMap map;

    public LoadTypeSet() {
        map = new DualMap();
        map.put(LoadType.DEFAULT);
    }

    public LoadTypeSet(String configId) {
        configure(new ConfigGetter<>(LoadTypeConfig.class).getConfig(configId));
    }

    public LoadTypeSet(LoadTypeConfig loadTypeConfig) {
        configure(loadTypeConfig);
    }

    public Map<String, LoadType> getNameMap() {
        return map.nameMap;
    }

    public Map<Integer, LoadType> getIdMap() {
        return map.idMap;
    }

    /**
     * Used by config to generate priorities for a name, and add them to the load type set.
     */
    public void addType(String name, String priority) {
        try {
            MessageDigest algorithm = MessageDigest.getInstance("MD5");
            algorithm.reset();
            algorithm.update(name.getBytes());
            byte messageDigest[] = algorithm.digest();

            int id = 0;
            for (int i = 0; i < 4; i++) {
                int temp = ((int)messageDigest[i] & 0xff);
                id <<= 8;
                id |= temp;
            }

            map.put(new LoadType(id, name, DocumentProtocol.Priority.valueOf(priority != null ? priority : "NORMAL_3")));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void addLoadType(int id, String name, DocumentProtocol.Priority priority) {
        map.put(new LoadType(id, name, priority));
    }

    public void configure(LoadTypeConfig config) {
        DualMap newMap = new DualMap();

        // Default should always be available.
        newMap.put(LoadType.DEFAULT);

        for (LoadTypeConfig.Type t : config.type()) {
            newMap.put(new LoadType(t.id(), t.name(), DocumentProtocol.Priority.valueOf(t.priority())));
        }

        map = newMap;
    }
}

