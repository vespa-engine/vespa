// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

/**
 * The spoolmaster program, which is used when multiple spooler instances are used to provide
 * multi colo HTTP feeding.
 * @author vegardh
 *
 */
public class VespaSpoolMaster extends AbstractService {

    public VespaSpoolMaster(AbstractConfigProducer parent, int index) {
        super(parent, "spoolmaster."+index);
    }

    @Override
    public int getPortCount() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getStartupCommand() {
        return "exec spoolmaster";
    }
}
