// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::index {

class Schema;
class WordDocElementWordPosFeatures;

/**
 * Interface used to build an index for the set of index fields specified in a schema.
 *
 *
 * The index should be built as follows:
 *   For each field add the set of unique words in sorted order.
 *   For each word add the set of document ids in sorted order.
 *   For each document id add the position information for that document.
 */
class IndexBuilder {
protected:
    const Schema &_schema;

public:
    IndexBuilder(const Schema &schema);

    virtual ~IndexBuilder();
    virtual void startField(uint32_t fieldId) = 0;
    virtual void endField() = 0;
    virtual void startWord(vespalib::stringref word) = 0;
    virtual void endWord() = 0;
    virtual void startDocument(uint32_t docId) = 0;
    virtual void endDocument() = 0;
    virtual void startElement(uint32_t elementId, int32_t weight, uint32_t elementLen) = 0;
    virtual void endElement() = 0;
    virtual void addOcc(const WordDocElementWordPosFeatures &features) = 0;
};

}
