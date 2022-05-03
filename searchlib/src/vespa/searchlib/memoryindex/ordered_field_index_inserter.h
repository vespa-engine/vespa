// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_ordered_field_index_inserter.h"
#include "field_index.h"
#include <limits>

namespace search::memoryindex {

class IFieldIndexInsertListener;

/**
 * Class used to insert inverted documents into a FieldIndex,
 * updating the underlying posting lists in that index.
 *
 * This is done by doing a single pass scan of the dictionary of the FieldIndex,
 * and for each word updating the posting list with docId adds / removes.
 *
 * Insert order must be properly sorted, first by word, then by docId.
 *
 * The template parameter specifies whether the posting lists of the field index have interleaved features or not.
 */
template <bool interleaved_features>
class OrderedFieldIndexInserter : public IOrderedFieldIndexInserter {
private:
    vespalib::stringref _word;
    uint32_t _prevDocId;
    bool     _prevAdd;
    using FieldIndexType = FieldIndex<interleaved_features>;
    using DictionaryTree = typename FieldIndexType::DictionaryTree;
    using PostingListStore = typename FieldIndexType::PostingListStore;
    using KeyComp = typename FieldIndexType::KeyComp;
    using WordKey = typename FieldIndexType::WordKey;
    using PostingListEntryType = typename FieldIndexType::PostingListEntryType;
    using PostingListKeyDataType = typename FieldIndexType::PostingListKeyDataType;
    FieldIndexType& _fieldIndex;
    typename DictionaryTree::Iterator _dItr;
    IFieldIndexInsertListener &_listener;

    // Pending changes to posting list for (_word)
    std::vector<uint32_t>    _removes;
    std::vector<PostingListKeyDataType> _adds;
    using WordEntry = std::tuple<vespalib::stringref, size_t, size_t>;
    std::vector<WordEntry> _word_entries;
    size_t _removes_offset;
    size_t _adds_offset;

    static constexpr uint32_t noFieldId = std::numeric_limits<uint32_t>::max();
    static constexpr uint32_t noDocId = std::numeric_limits<uint32_t>::max();

    /**
     * Flush pending changes to postinglist for (_word).
     *
     * _dItr is located at correct position.
     */
    void flushWord();

public:
    OrderedFieldIndexInserter(FieldIndexType& fieldIndex);
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


    void commit() override;

    /**
     * Rewind iterator, to start new pass.
     */
    void rewind() override;

    vespalib::datastore::EntryRef getWordRef() const override;
};

}
