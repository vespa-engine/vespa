// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.messagebus.routing.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Abstract class for policies that allow you to specify which slobrok to use for the routing.
 */
public abstract class SlobrokPolicy implements DocumentProtocolRoutingPolicy {

    private boolean firstTry = true;

    protected List<Mirror.Entry> lookup(RoutingContext context, String pattern) {
        IMirror mirror1 =  context.getMirror();

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

    public static Map<String, String> parse(String param) {
        Map<String, String> map = new TreeMap<>();

        if (param != null) {
            String[] p = param.split(";");
            for (String s : p) {
                String[] keyValue = s.split("=");

                if (keyValue.length == 1) {
                    map.put(keyValue[0], "true");
                } else if (keyValue.length == 2) {
                    map.put(keyValue[0], keyValue[1]);
                }
            }
        }

        return map;
    }

}
