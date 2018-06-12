// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memoryfieldindex.h"

namespace search::memoryindex {

class IDocumentRemoveListener;
class FieldInverter;

class Dictionary {
public:
    using PostingList = MemoryFieldIndex::PostingList;

private:
    typedef vespalib::GenerationHandler GenerationHandler;

    std::vector<std::unique_ptr<MemoryFieldIndex> > _fieldIndexes;
    uint32_t                _numFields;

public:
    Dictionary(const index::Schema &schema);
    ~Dictionary();
    PostingList::Iterator find(const vespalib::stringref word,
                               uint32_t fieldId) const
    {
        return _fieldIndexes[fieldId]->find(word);
    }

    PostingList::ConstIterator
    findFrozen(const vespalib::stringref word, uint32_t fieldId) const
    {
        return _fieldIndexes[fieldId]->findFrozen(word);
    }

    uint64_t getNumUniqueWords() const {
        uint64_t numUniqueWords = 0;
        for (auto &fieldIndex : _fieldIndexes) {
            numUniqueWords += fieldIndex->getNumUniqueWords();
        }
        return numUniqueWords;
    }

    void dump(search::index::IndexBuilder & indexBuilder);

    MemoryUsage getMemoryUsage() const;

    MemoryFieldIndex *getFieldIndex(uint32_t fieldId) const {
        return _fieldIndexes[fieldId].get();
    }

    const std::vector<std::unique_ptr<MemoryFieldIndex> > &
    getFieldIndexes() const { return _fieldIndexes; }

    uint32_t getNumFields() const { return _numFields; }
};

}
