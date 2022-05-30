// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

/**
 * This is a class containing the global ids that are given out.
 * Must be in sync with version for c++ in vespalib/src/vespalib/objects/ids.h
 *
 * @author baldersheim
 */
public interface Ids {

    int document = 0x1000;
    int searchlib = 0x4000;
    int vespa_configmodel = 0x7000;
    int annotation = 0x10000;

}
