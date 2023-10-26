// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::memoryindex {

/**
 * Interface used to track which {word, docId} pairs that are removed from a FieldIndex.
 */
class IFieldIndexRemoveListener {
public:
    virtual ~IFieldIndexRemoveListener() {}

    /**
     * Called when a {word, docId} tuple is removed from the field index.
     */
    virtual void remove(const vespalib::stringref word, uint32_t docId) = 0;
};

}
