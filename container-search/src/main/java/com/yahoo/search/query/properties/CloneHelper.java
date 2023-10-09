// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.search.result.Hit;

/**
 * Extends com.yahoo.processing.request.CloneHelper with fastpath for
 *  - com.yahoo.search.result.Hit
 */
public class CloneHelper extends com.yahoo.processing.request.CloneHelper {

    @Override
    protected Object objectClone(Object object) {
        if (object instanceof Hit) {
            return ((Hit)object).clone();
        }
        return super.objectClone(object);
    }

}
