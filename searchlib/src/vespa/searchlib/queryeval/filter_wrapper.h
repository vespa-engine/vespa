// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

namespace search::queryeval {

/**
 * Wraps an iterator for use as a filter search.
 * Owns TermFieldMatchData the wrapped iterator
 * can wire to, and write to if necessary.
 **/
class FilterWrapper : public SearchIterator {
private:
    std::vector<fef::TermFieldMatchData> _unused_md;
    fef::TermFieldMatchDataArray         _tfmda;
    std::unique_ptr<SearchIterator>      _wrapped_search;
public:
    explicit FilterWrapper(size_t num_fields)
      : _unused_md(num_fields),
        _tfmda(),
        _wrapped_search()
    {
        for (size_t i = 0; i < num_fields; ++i) {
            _tfmda.add(&_unused_md[i]);
        }
    }
    const fef::TermFieldMatchDataArray& tfmda() const { return _tfmda; }
    void wrap(std::unique_ptr<SearchIterator> wrapped) {
        _wrapped_search = std::move(wrapped);
    }
    void wrap(SearchIterator *wrapped) {
        _wrapped_search.reset(wrapped);
    }
    void doSeek(uint32_t docid) override {
        _wrapped_search->seek(docid);          // use outer seek for most robustness
        setDocId(_wrapped_search->getDocId()); // propagate current iterator docid
    }
    void doUnpack(uint32_t) override {}
    void initRange(uint32_t begin_id, uint32_t end_id) override {
        SearchIterator::initRange(begin_id, end_id);
        _wrapped_search->initRange(begin_id, end_id);
        setDocId(_wrapped_search->getDocId());
    }
    void or_hits_into(BitVector &result, uint32_t begin_id) override {
        _wrapped_search->or_hits_into(result, begin_id);
    }
    void and_hits_into(BitVector &result, uint32_t begin_id) override {
        _wrapped_search->and_hits_into(result, begin_id);
    }
    BitVector::UP get_hits(uint32_t begin_id) override {
        return _wrapped_search->get_hits(begin_id);
    }
    BitVectorMeta asBitVector() const noexcept override {
        return _wrapped_search->asBitVector();
    }
    Trinary is_strict() const override {
        return _wrapped_search->is_strict();
    }
};

} // namespace
