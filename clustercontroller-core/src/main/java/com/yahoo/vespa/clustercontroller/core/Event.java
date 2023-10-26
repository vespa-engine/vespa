// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public interface Event {

    long getTimeMs();
    String getDescription();
    String getCategory();

}
