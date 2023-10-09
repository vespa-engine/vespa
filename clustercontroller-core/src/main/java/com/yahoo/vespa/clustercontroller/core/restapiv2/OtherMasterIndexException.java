// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

public class OtherMasterIndexException extends Exception {

    private final int index;

    public OtherMasterIndexException(int index) {
        this.index = index;
    }

    public int getMasterIndex() { return index; }

}
