// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_field_index_collection.h"
#include "i_field_index.h"
#include <memory>
#include <vector>

namespace search::index {
    class IFieldLengthInspector;
    class Schema;
}

namespace search::memoryindex {

class IFieldIndexRemoveListener;
class FieldInverter;

/**
 * The collection of all field indexes that are part of a memory index.
 *
 * Provides functions to create a posting list iterator (used for searching)
 * for a given word in a given field.
 */
class FieldIndexCollection : public IFieldIndexCollection {
private:
    using GenerationHandler = vespalib::GenerationHandler;

    std::vector<std::unique_ptr<IFieldIndex>> _fieldIndexes;
    uint32_t                _numFields;

public:
    FieldIndexCollection(const index::Schema& schema, const index::IFieldLengthInspector& inspector);
    ~FieldIndexCollection() override;

    uint64_t getNumUniqueWords() const {
        uint64_t numUniqueWords = 0;
        for (auto &fieldIndex : _fieldIndexes) {
            numUniqueWords += fieldIndex->getNumUniqueWords();
        }
        return numUniqueWords;
    }

    void dump(search::index::IndexBuilder & indexBuilder);

    vespalib::MemoryUsage getMemoryUsage() const;

    IFieldIndex *getFieldIndex(uint32_t fieldId) const {
        return _fieldIndexes[fieldId].get();
    }

    const std::vector<std::unique_ptr<IFieldIndex>> &getFieldIndexes() const { return _fieldIndexes; }

    uint32_t getNumFields() const { return _numFields; }

    FieldIndexRemover &get_remover(uint32_t field_id) override;
    IOrderedFieldIndexInserter &get_inserter(uint32_t field_id) override;
    index::FieldLengthCalculator &get_calculator(uint32_t field_id) override;
};

}
