// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::memoryindex {

/**
 * Interface used to track which {wordRef, fieldId} pairs that are
 * removed from the memory index dictionary for a document.
 */
class IDocumentRemoveListener
{
public:
    virtual ~IDocumentRemoveListener() {}
    virtual void remove(const vespalib::stringref word, uint32_t docId) = 0;
};

}
