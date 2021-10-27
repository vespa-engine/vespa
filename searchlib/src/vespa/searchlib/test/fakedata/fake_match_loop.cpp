// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_match_loop.h"
#include "fakeposting.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/searchiterator.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::queryeval::AndSearch;
using search::queryeval::OrSearch;
using search::queryeval::SearchIterator;

namespace search::fakedata {

namespace {

class IteratorState {
private:
    TermFieldMatchData _md;
    TermFieldMatchDataArray _tfmda;
    std::unique_ptr<SearchIterator> _itr;

public:
    IteratorState(const FakePosting& posting)
        : _md(),
          _tfmda(),
          _itr()
    {
        _tfmda.add(&_md);
        _md.setNeedNormalFeatures(posting.enable_unpack_normal_features());
        _md.setNeedInterleavedFeatures(posting.enable_unpack_interleaved_features());
        _itr.reset(posting.createIterator(_tfmda));
    }
    ~IteratorState() {}

    SearchIterator& itr() { return *_itr; }
    SearchIterator* release() { return _itr.release(); }
};

template <bool do_unpack>
int
do_match_loop(SearchIterator& itr, uint32_t doc_id_limit)
{
    uint32_t hits = 0;
    itr.initFullRange();
    uint32_t doc_id = itr.getDocId();
    while (doc_id < doc_id_limit) {
        if (itr.seek(doc_id)) {
            ++hits;
            if (do_unpack) {
                itr.unpack(doc_id);
            }
            ++doc_id;
        } else if (doc_id < itr.getDocId()) {
            doc_id = itr.getDocId();
        } else {
            ++doc_id;
        }
    }
    return hits;
}

}

int
FakeMatchLoop::direct_posting_scan(const FakePosting& posting, uint32_t doc_id_limit)
{
    IteratorState state(posting);
    return do_match_loop<false>(state.itr(), doc_id_limit);
}

int
FakeMatchLoop::direct_posting_scan_with_unpack(const FakePosting& posting, uint32_t doc_id_limit)
{
    IteratorState state(posting);
    return do_match_loop<true>(state.itr(), doc_id_limit);
}

int
FakeMatchLoop::and_pair_posting_scan(const FakePosting& posting_1, const FakePosting& posting_2, uint32_t doc_id_limit)
{
    IteratorState state_1(posting_1);
    IteratorState state_2(posting_2);
    std::unique_ptr<SearchIterator> iterator(AndSearch::create({state_1.release(), state_2.release()}, true));
    return do_match_loop<false>(*iterator, doc_id_limit);
}

int
FakeMatchLoop::and_pair_posting_scan_with_unpack(const FakePosting& posting_1, const FakePosting& posting_2, uint32_t doc_id_limit)
{
    IteratorState state_1(posting_1);
    IteratorState state_2(posting_2);
    std::unique_ptr<SearchIterator> iterator(AndSearch::create({state_1.release(), state_2.release()}, true));
    return do_match_loop<true>(*iterator, doc_id_limit);
}

int
FakeMatchLoop::or_pair_posting_scan(const FakePosting& posting_1, const FakePosting& posting_2, uint32_t doc_id_limit)
{
    IteratorState state_1(posting_1);
    IteratorState state_2(posting_2);
    std::unique_ptr<SearchIterator> iterator(OrSearch::create({state_1.release(), state_2.release()}, true));
    return do_match_loop<false>(*iterator, doc_id_limit);
}

int
FakeMatchLoop::or_pair_posting_scan_with_unpack(const FakePosting& posting_1, const FakePosting& posting_2, uint32_t doc_id_limit)
{
    IteratorState state_1(posting_1);
    IteratorState state_2(posting_2);
    std::unique_ptr<SearchIterator> iterator(OrSearch::create({state_1.release(), state_2.release()}, true));
    return do_match_loop<true>(*iterator, doc_id_limit);
}

}

