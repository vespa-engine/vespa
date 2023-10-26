// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

/**
 * To avoid having to catch clone not supported exception everywhere, and create code with lack of
 * coverage, this class exist to hide the clone not supported exceptions that should never happen.
 */
public class CertainlyCloneable<T> implements Cloneable {

    @Override
    public Object clone() {
        try{
            return callParentClone();
        } catch (CloneNotSupportedException e) {
                // Super clone should never throw exception for objects that should certainly be cloneable.
            throw new Error(e);
        }
    }

    protected Object callParentClone() throws CloneNotSupportedException {
        return super.clone();
    }

}
