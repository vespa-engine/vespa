// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.osgi.provider.model.ComponentModel;

/**
 * @author stiankri
 */
public class Servlet extends SimpleComponent {
    public final String bindingPath;

    public Servlet(ComponentModel componentModel, String bindingPath) {
        super(componentModel);
        this.bindingPath = bindingPath;
    }

    public ServletPathsConfig.Servlets.Builder toConfigBuilder() {
        return new ServletPathsConfig.Servlets.Builder()
                .path(bindingPath);
    }
}
