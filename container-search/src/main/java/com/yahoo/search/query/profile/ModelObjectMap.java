// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.properties.PropertyMap;

/**
 * A map which stores all types which cannot be stored in a query profile
 * that is rich model objects.
 * <p>
 * This map will deep copy not only the model object map, but also each
 * clonable member in the map.
 *
 * @author bratseth
 */
public class ModelObjectMap extends PropertyMap {

    /**
     * Returns true if the class of the value is *not* acceptable as a query profile value,
     * and therefore should be set in this.
     */
    @Override
    protected boolean shouldSet(CompoundName name, Object value) {
        if (value == null) return true;
        return ! FieldType.isLegalFieldValue(value);
    }

}
