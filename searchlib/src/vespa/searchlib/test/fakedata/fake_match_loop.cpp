// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_match_loop.h"
#include "fakeposting.h"
#include <vespa/fastos/timestamp.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/searchiterator.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
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
        _itr.reset(posting.createIterator(_tfmda));
    }
    ~IteratorState() {}

    SearchIterator& itr() { return *_itr; }
};

template <bool do_unpack>
int
do_single_posting_scan(SearchIterator& itr, uint32_t doc_id_limit, uint64_t& elapsed_time_ns)
{
    uint32_t hits = 0;
    uint64_t time_before = fastos::ClockSystem::now();
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
    uint64_t time_after = fastos::ClockSystem::now();
    elapsed_time_ns = time_after - time_before;
    return hits;
}

}

int
FakeMatchLoop::single_posting_scan(const FakePosting& posting, uint32_t doc_id_limit, uint64_t& elapsed_time_ns)
{
    IteratorState state(posting);
    return do_single_posting_scan<false>(state.itr(), doc_id_limit, elapsed_time_ns);
}

int
FakeMatchLoop::single_posting_scan_with_unpack(const FakePosting& posting, uint32_t doc_id_limit, uint64_t& elapsed_time_ns)
{
    IteratorState state(posting);
    return do_single_posting_scan<true>(state.itr(), doc_id_limit, elapsed_time_ns);
}

namespace {

template <bool do_unpack>
int
do_and_pair_posting_scan(SearchIterator& itr1, SearchIterator& itr2,
                         uint32_t doc_id_limit, uint64_t& elapsed_time_ns)
{
    uint32_t hits = 0;
    uint64_t time_before = fastos::ClockSystem::now();
    itr1.initFullRange();
    itr2.initFullRange();
    uint32_t doc_id = itr1.getDocId();
    while (doc_id < doc_id_limit) {
        if (itr1.seek(doc_id)) {
            if (itr2.seek(doc_id)) {
                ++hits;
                if (do_unpack) {
                    itr1.unpack(doc_id);
                    itr2.unpack(doc_id);
                }
                ++doc_id;
            } else if (doc_id < itr2.getDocId()) {
                doc_id = itr2.getDocId();
            } else {
                ++doc_id;
            }
        } else if (doc_id < itr1.getDocId()) {
            doc_id = itr1.getDocId();
        } else {
            ++doc_id;
        }
    }
    uint64_t time_after = fastos::ClockSystem::now();
    elapsed_time_ns = time_after - time_before;
    return hits;
}

}

int
FakeMatchLoop::and_pair_posting_scan(const FakePosting& posting_1, const FakePosting& posting_2,
                                     uint32_t doc_id_limit, uint64_t& elapsed_time_ns)
{
    IteratorState state_1(posting_1);
    IteratorState state_2(posting_2);
    return do_and_pair_posting_scan<false>(state_1.itr(), state_2.itr(), doc_id_limit, elapsed_time_ns);
}

int
FakeMatchLoop::and_pair_posting_scan_with_unpack(const FakePosting& posting_1, const FakePosting& posting_2,
                                                 uint32_t doc_id_limit, uint64_t& elapsed_time_ns)
{
    IteratorState state_1(posting_1);
    IteratorState state_2(posting_2);
    return do_and_pair_posting_scan<true>(state_1.itr(), state_2.itr(), doc_id_limit, elapsed_time_ns);
}

}

