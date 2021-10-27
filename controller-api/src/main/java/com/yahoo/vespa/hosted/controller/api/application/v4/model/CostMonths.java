// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.List;

/**
 * @author ogronnesby
 */
public class CostMonths {
    public List<String> months;

    public List<String> getMonths() {
        return months;
    }
}
