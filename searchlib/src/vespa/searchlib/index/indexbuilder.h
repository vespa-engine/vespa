// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::index {

class DocIdAndFeatures;
class Schema;
class WordDocElementWordPosFeatures;

/**
 * Interface for building an index for a single field
 * The index should be built as follows:
 *   Add the set of unique words in sorted order.
 *   For each word add the set of document ids in sorted order.
 *   For each document id add the position information for that document.
 */
class FieldIndexBuilder {
public:
    virtual ~FieldIndexBuilder() = default;
    virtual void startWord(std::string_view word) = 0;
    virtual void endWord() = 0;
    virtual void add_document(const DocIdAndFeatures &features) = 0;
};

/**
 * Interface used to build an index for the set of index fields specified in a schema.
 * Create and complete one field builder at the time.
 */
class IndexBuilder {
protected:
    const Schema &_schema;
public:
    explicit IndexBuilder(const Schema &schema);
    virtual ~IndexBuilder();
    virtual std::unique_ptr<FieldIndexBuilder> startField(uint32_t fieldId) = 0;
};

}
