// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Handler;

/**
 * @author Einar M R Rosenvinge
 */
public class MbusClient extends Handler implements SessionConfig.Producer {
    private static final ComponentSpecification CLASSNAME =
            ComponentSpecification.fromString("com.yahoo.container.jdisc.messagebus.MbusClientProvider");

    private final String sessionName;
    private final SessionConfig.Type.Enum type;

    public MbusClient(String sessionName, SessionConfig.Type.Enum type) {
        super(new ComponentModel(new BundleInstantiationSpecification(createId(sessionName), CLASSNAME, null)));
        this.sessionName = sessionName;
        this.type = type;
    }

    private static ComponentId createId(String sessionName) {
        return ComponentId.fromString(sessionName).nestInNamespace(
                ComponentId.fromString("MbusClient"));
    }

    @Override
    public void getConfig(SessionConfig.Builder sb) {
        sb.
            name(sessionName).
            type(type);
    }
    
    public String getSessionName() {
        return sessionName;
    }
}
