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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract class for policies that allow you to specify which slobrok to use for the
 * routing.
 */
public abstract class ExternalSlobrokPolicy extends AsyncInitializationPolicy implements ConfigSubscriber.SingleSubscriber<SlobroksConfig> {
    String error;
    private Supervisor orb = null;
    private final AtomicReference<Mirror> safeMirror = new AtomicReference<>(null);
    private SlobrokList slobroks = null;
    private boolean firstTry = true;
    private ConfigSubscriber subscriber;
    String[] configSources = null;
    private final static String slobrokConfigId = "admin/slobrok.0";


    ExternalSlobrokPolicy(Map<String, String> param) {
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
    public synchronized void init() {
        if (slobroks != null) {
            orb = new Supervisor(new Transport());
            safeMirror.set(new Mirror(orb, slobroks));
        }

        if (configSources != null) {
            if (safeMirror.get() == null) {
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
        return safeMirror.get();
    }

    public  List<Mirror.Entry> lookup(RoutingContext context, String pattern) {
        IMirror mirror1 = getMirror();
        if (mirror1 == null) {
            mirror1 = context.getMirror();
        }

        List<Mirror.Entry> arr = mirror1.lookup(pattern);

        if (arr.isEmpty()) {
            synchronized(this)  {
                if (firstTry) {
                    try {
                        int count = 0;
                        while (arr.isEmpty() && count < 100) {
                            Thread.sleep(50);
                            arr = mirror1.lookup(pattern);
                            count++;
                        }
                    } catch (InterruptedException e) {
                    }
                    firstTry = true;
                }

            }
        }

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
        if (safeMirror.get() == null) {
            safeMirror.set(new Mirror(orb, slobroks));
        }

    }

    @Override
    public synchronized void destroy() {
        if (subscriber!=null) subscriber.close();
    }

}
