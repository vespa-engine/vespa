// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.osgi.provider.model.ComponentModel;

/**
 * Sets up VipStatusHandler that answers OK when a certain file is present.
 *
 * @author Tony Vaagenes
 */
public class FileStatusHandlerComponent extends Handler implements VipStatusConfig.Producer {

    public static final String CLASS = "com.yahoo.container.handler.VipStatusHandler";

    private final String fileName;

    public FileStatusHandlerComponent(String id, String fileName, String... bindings) {
        super(new ComponentModel(id, CLASS, null, null));

        this.fileName = fileName;
        addServerBindings(bindings);
    }

    @Override
    public void getConfig(VipStatusConfig.Builder builder) {
        builder.accessdisk(true).
                statusfile(fileName);
    }

}
