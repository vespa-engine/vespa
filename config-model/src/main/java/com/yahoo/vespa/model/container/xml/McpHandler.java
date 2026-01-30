// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;

import java.util.Collection;
import java.util.List;

/**
 * Component definition for {@link ai.vespa.mcp.McpRequestHandler}
 *
 * @author bjorncs
 */
class McpHandler extends Handler {

    static final String HANDLER_CLASS = "ai.vespa.mcp.McpRequestHandler";
    static final String BUNDLE = "container-disc";
    static final BindingPattern DEFAULT_BINDING = SystemBindingPattern.fromHttpPath("/mcp/*");

    McpHandler() {
        super(new ComponentModel(HANDLER_CLASS, null, BUNDLE, null));
    }

    static List<BindingPattern> bindingPattern(Collection<Integer> ports) {
        if (ports.isEmpty()) return defaultBindings();
        return ports.stream()
                .map(p -> (BindingPattern) SystemBindingPattern.fromHttpPortAndPath(p, DEFAULT_BINDING.path()))
                .toList();
    }

    static List<BindingPattern> defaultBindings() {
        return List.of(DEFAULT_BINDING);
    }

}
