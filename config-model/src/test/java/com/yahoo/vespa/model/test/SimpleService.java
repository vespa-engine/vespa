// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.test.StandardConfig.Builder;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;

import java.util.HashMap;
import java.util.Optional;

/**
 * This service has a desired default port and returns the actual
 * baseport from getConfig().
 *
 * @author  gjoranv
 */
public class SimpleService extends AbstractService implements com.yahoo.test.StandardConfig.Producer {

    /**
     * Creates a new SimpleService instance
     *
     * @param parent     The parent ConfigProducer.
     * @param name       Service name
     */
    public SimpleService(AbstractConfigProducer parent, String name) {
        super(parent, name);
        portsMeta.on(0).tag("base")
            .on(1).tag("base")
            .on(2).tag("base")
            .on(3).tag("base")
            .on(4).tag("base");
    }

    @Override
    public void getConfig(Builder builder) {
        builder.astring("simpleservice").baseport(getRelativePort(0));
    }
    
    public int getWantedPort(){ return 10000; }
    public int getPortCount() { return 5; }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = getWantedPort();
        from.wantPort(start++, "a");
        from.wantPort(start++, "b");
        from.wantPort(start++, "c");
        from.wantPort(start++, "d");
        from.wantPort(start++, "e");
    }

    // Make sure this service is listed in the sentinel config
    public Optional<String> getStartupCommand()   { return Optional.of("sleep 0"); }

    @Override
       public HashMap<String,String> getDefaultMetricDimensions(){
        HashMap<String, String> dimensions = new HashMap<>();
        dimensions.put("clustername", "testClusterName");
        return dimensions;
    }

}
