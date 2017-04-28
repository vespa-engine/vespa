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
