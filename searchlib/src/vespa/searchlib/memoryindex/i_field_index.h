// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <memory>

namespace search::queryeval {
    struct SimpleLeafBlueprint;
    class FieldSpec;
}
namespace search::index {
class FieldLengthCalculator;
class IndexBuilder;
}

namespace search::memoryindex {

class FeatureStore;
class FieldIndexRemover;
class IOrderedFieldIndexInserter;
class WordStore;

/**
 * Interface for a memory index for a single field as seen from the FieldIndexCollection.
 */
class IFieldIndex {
public:
    virtual ~IFieldIndex() = default;

    virtual uint64_t getNumUniqueWords() const = 0;
    virtual vespalib::MemoryUsage getMemoryUsage() const = 0;
    virtual const FeatureStore& getFeatureStore() const = 0;
    virtual const WordStore& getWordStore() const = 0;
    virtual IOrderedFieldIndexInserter& getInserter() = 0;
    virtual FieldIndexRemover& getDocumentRemover() = 0;
    virtual index::FieldLengthCalculator& get_calculator() = 0;
    virtual void compactFeatures() = 0;
    virtual void dump(search::index::IndexBuilder& indexBuilder) = 0;

    virtual std::unique_ptr<queryeval::SimpleLeafBlueprint> make_term_blueprint(const vespalib::string& term,
                                                                                const queryeval::FieldSpec& field,
                                                                                uint32_t field_id) = 0;

    // Should only be directly used by unit tests
    virtual vespalib::GenerationHandler::Guard takeGenerationGuard() = 0;
    virtual void commit() = 0;
};

}
