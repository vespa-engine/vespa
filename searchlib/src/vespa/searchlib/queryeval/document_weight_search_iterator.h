// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/attribute/i_docid_with_weight_posting_store.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::queryeval {

class DocumentWeightSearchIterator : public SearchIterator
{
private:
    fef::TermFieldMatchData          &_tfmd;
    fef::TermFieldMatchDataPosition * _matchPosition;
    DocidWithWeightIterator            _iterator;
    queryeval::MinMaxPostingInfo      _postingInfo;

public:
    DocumentWeightSearchIterator(fef::TermFieldMatchData &tfmd,
                                 const IDocidWithWeightPostingStore &attr,
                                 IDirectPostingStore::LookupResult dict_entry)
        : _tfmd(tfmd),
          _matchPosition(_tfmd.populate_fixed()),
          _iterator(attr.create(dict_entry.posting_idx)),
          _postingInfo(queryeval::MinMaxPostingInfo(dict_entry.min_weight, dict_entry.max_weight))
    { }
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        _iterator.lower_bound(begin);
        updateDocId();
    }
    void updateDocId() {
        if (_iterator.valid()) {
            setDocId(_iterator.getKey());
        } else {
            setAtEnd();
        }
    }

    void doSeek(uint32_t docId) override {
        _iterator.linearSeek(docId);
        updateDocId();
    }

    void doUnpack(uint32_t docId) override {
        _tfmd.resetOnlyDocId(docId);
        _matchPosition->setElementWeight(_iterator.getData());
    }

    const queryeval::PostingInfo *getPostingInfo() const override { return &_postingInfo; }
    Trinary is_strict() const override { return Trinary::True; }
};

}
