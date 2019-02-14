// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.test.StandardConfig.Builder;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

import java.util.HashMap;

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
    public String[] getPortSuffixes() {
        String[] suffixes = new String[5];
        suffixes[0] = "a";
        suffixes[1] = "b";
        suffixes[2] = "c";
        suffixes[3] = "d";
        suffixes[4] = "e";
        return suffixes;
    }

    // Make sure this service is listed in the sentinel config
    public String getStartupCommand()   { return "sleep 0"; }

    public boolean getAutostartFlag()   { return false; }
    public boolean getAutorestartFlag() { return false; }

    @Override
       public HashMap<String,String> getDefaultMetricDimensions(){
        HashMap<String, String> dimensions = new HashMap<>();
        dimensions.put("clustername", "testClusterName");
        return dimensions;
    }

}
