// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

namespace search::queryeval {

/**
 * A term iterator wrapper used to hide detailed match
 * information. Wrapping a term iterator with an instance of this
 * class will ensure that the unpack method will only disclose whether
 * we found a match or not. This is done by intercepting calls to the
 * doUnpack method. The doSeek method will be forwarded to ensure we
 * match the same set of documents.
 **/
class BooleanMatchIteratorWrapper : public SearchIterator
{
private:
    SearchIterator::UP       _search;
    fef::TermFieldMatchData *_tfmdp;

protected:
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override { return _search->is_strict(); }
    void initRange(uint32_t beginid, uint32_t endid) override {
        _search->initRange(beginid, endid);
        SearchIterator::initRange(_search->getDocId()+1, _search->getEndId());
    }

public:
    /**
     * Create a wrapper for the given search using the given term
     * match data. This object will take ownership of the given search
     * and delete it in the destructor. The given search must be a
     * term iterator that is using the given term match data to store
     * its matching details during unpack. The given term match data
     * is expected to be stored inside a match data object and as such
     * be managed outside of this object.  The iterator will fill in
     * match/non-match information only, and only if the given array
     * holds exactly one reference.
     *
     * @param search internal search, must be a term iterator
     * @param match term match data used by the internal iterator
     **/
    BooleanMatchIteratorWrapper(SearchIterator::UP search, const fef::TermFieldMatchDataArray &matchData);

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
};

}
