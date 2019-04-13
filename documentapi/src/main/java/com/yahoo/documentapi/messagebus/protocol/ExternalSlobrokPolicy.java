// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.cloud.config.SlobroksConfig;

import java.util.Map;

/**
 * Abstract class for policies that allow you to specify which slobrok to use for the
 * routing.
 */
public abstract class ExternalSlobrokPolicy extends AsyncInitializationPolicy implements ConfigSubscriber.SingleSubscriber<SlobroksConfig> {
    String error;
    Supervisor orb = null;
    Mirror mirror = null;
    SlobrokList slobroks = null;
    boolean firstTry = true;
    private ConfigSubscriber subscriber;
    String[] configSources = null;
    String slobrokConfigId = "admin/slobrok.0";


    public ExternalSlobrokPolicy(Map<String, String> param) {
        super();

        String conf = param.get("config");
        if (conf != null) {
            configSources = conf.split(",");
        }

        String slbrk = param.get("slobroks");
        if (slbrk != null) {
            slobroks = new SlobrokList();
            slobroks.setup(slbrk.split(","));
        }

        if (slobroks != null || configSources != null) {
            needAsynchronousInitialization();
        }
    }

    @Override
    public void init() {
        if (slobroks != null) {
            orb = new Supervisor(new Transport());
            mirror = new Mirror(orb, slobroks);
        }

        if (configSources != null) {
            if (mirror == null) {
                orb = new Supervisor(new Transport());
                subscriber = subscribe(slobrokConfigId, new ConfigSourceSet(configSources));
            }
        }
    }

    private ConfigSubscriber subscribe(String configId, final ConfigSourceSet configSourceSet) {
        ConfigSubscriber subscriber = new ConfigSubscriber(configSourceSet);
        subscriber.subscribe(this, SlobroksConfig.class, configId);
        return subscriber;
    }

    public IMirror getMirror() {
        return mirror;
    }

    public  Mirror.Entry[] lookup(RoutingContext context, String pattern) {
        IMirror mirror1 = (mirror != null ? mirror : context.getMirror());

        Mirror.Entry[] arr = mirror1.lookup(pattern);

        if ((arr.length == 0) && firstTry) {
            synchronized(this)  {
                try {
                    int count = 0;
                    while (arr.length == 0 && count < 100) {
                        Thread.sleep(50);
                        arr = mirror1.lookup(pattern);
                        count++;
                    }
                } catch (InterruptedException e) {
                }

            }
        }

        firstTry = false;
        return arr;
    }

    @Override
    public synchronized void configure(SlobroksConfig config) {
        String[] slist = new String[config.slobrok().size()];

        for(int i = 0; i < config.slobrok().size(); i++) {
            slist[i] = config.slobrok(i).connectionspec();
        }
        if (slobroks == null) {
            slobroks = new SlobrokList();
        }
        slobroks.setup(slist);
        if (mirror == null) {
            mirror = new Mirror(orb, slobroks);
        }

    }

    @Override
    public void destroy() {
        if (subscriber!=null) subscriber.close();
    }

}
