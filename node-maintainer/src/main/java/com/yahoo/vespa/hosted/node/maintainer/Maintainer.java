// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import com.yahoo.container.logging.AccessLog;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.node.maintainer.restapi.v1.MaintainerApiHandler;

/**
 * @author freva
 */
public class Maintainer {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new RuntimeException("Expected only 1 argument - a JSON list of maintainer jobs to execute");
        }

        MaintainerApiHandler handler = new MaintainerApiHandler(Runnable::run, AccessLog.voidAccessLog());
        Inspector object = SlimeUtils.jsonToSlime(args[0].getBytes()).get();
        if (object.type() != Type.ARRAY) {
            throw new IllegalArgumentException("Expected a list maintainer jobs to execute");
        }

        object.traverse((ArrayTraverser) (int i, Inspector item) -> {
            String type = handler.getFieldOrFail(item, "type").asString();
            Inspector arguments = handler.getFieldOrFail(item, "arguments");
            handler.parseMaintenanceJob(type, arguments);
        });
    }
}
