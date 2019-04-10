// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_ordered_field_index_inserter.h"
#include "field_index.h"
#include <limits>

namespace search::memoryindex {

class IDocumentInsertListener;

/**
 * Class used to insert inverted documents into a FieldIndex,
 * updating the underlying posting lists in that index.
 *
 * This is done by doing a single pass scan of the dictionary of the FieldIndex,
 * and for each word updating the posting list with docId adds / removes.
 *
 * Insert order must be properly sorted, first by word, then by docId.
 */
class OrderedFieldIndexInserter : public IOrderedFieldIndexInserter {
private:
    vespalib::stringref _word;
    uint32_t _prevDocId;
    bool     _prevAdd;
    using DictionaryTree = FieldIndex::DictionaryTree;
    using PostingListStore = FieldIndex::PostingListStore;
    using KeyComp = FieldIndex::KeyComp;
    using WordKey = FieldIndex::WordKey;
    using PostingListKeyDataType = FieldIndex::PostingListKeyDataType;
    FieldIndex        &_fieldIndex;
    DictionaryTree::Iterator _dItr;
    IDocumentInsertListener &_listener;

    // Pending changes to posting list for (_word)
    std::vector<uint32_t>    _removes;
    std::vector<PostingListKeyDataType> _adds;


    static constexpr uint32_t noFieldId = std::numeric_limits<uint32_t>::max();
    static constexpr uint32_t noDocId = std::numeric_limits<uint32_t>::max();

    /**
     * Flush pending changes to postinglist for (_word).
     *
     * _dItr is located at correct position.
     */
    void flushWord();

public:
    OrderedFieldIndexInserter(FieldIndex &fieldIndex);
    ~OrderedFieldIndexInserter() override;
    void setNextWord(const vespalib::stringref word) override;
    void add(uint32_t docId, const index::DocIdAndFeatures &features) override;
    void remove(uint32_t docId) override;

    /**
     * Flush pending changes to postinglist for (_word).  Also flush
     * insert listener.
     *
     * _dItr is located at correct position.
     */
    void flush() override;

    /**
     * Rewind iterator, to start new pass.
     */
    void rewind() override;

    // Used by unit test
    datastore::EntryRef getWordRef() const;
};

}
