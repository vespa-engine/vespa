// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;

/**
 * This is the clients plugin for the Vespa model. It is responsible for creating
 * all clients services.
 *
 * @author Gunnar Gauslaa Bergem
 */
public class Clients extends ConfigModel {

    private static final long serialVersionUID = 1L;
    private LoadTypeSet loadTypes = new LoadTypeSet();
    
    public Clients(ConfigModelContext modelContext) {
        super(modelContext);
    }

    public LoadTypeSet getLoadTypes() {
        return loadTypes;
    }

    public void getConfig(LoadTypeConfig.Builder builder) {
        for (LoadType t : loadTypes.getNameMap().values()) {
            if (t != LoadType.DEFAULT) {
                builder.type(getLoadTypeConfig(t));
            }
        }
    }

    private LoadTypeConfig.Type.Builder getLoadTypeConfig(LoadType loadType) {
        LoadTypeConfig.Type.Builder builder = new LoadTypeConfig.Type.Builder();
        builder.name(loadType.getName());
        builder.id(loadType.getId());
        builder.priority(loadType.getPriority().toString());
        return builder;
    }

}
