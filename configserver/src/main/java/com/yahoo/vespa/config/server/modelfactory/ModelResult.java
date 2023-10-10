// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.config.model.api.Model;

/**
 * @author bratseth
 */
public interface ModelResult {

    Model getModel();

}
