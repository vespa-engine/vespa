// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.io.Serializable;

/**
 * Properties for yamas monitoring service
 *
 * @author hmusum
 * @since 5.1.20
 */
public class Yamas extends AbstractMonitoringSystem implements Serializable {

    public Yamas(String clustername, Integer interval) {
        super(clustername, interval);
    }

}


