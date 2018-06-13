// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_search.h"
#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/searchcommon/attribute/i_search_context.h>

namespace search {
namespace queryeval {

namespace {

struct FakeContext : search::attribute::ISearchContext {
    int32_t onFind(DocId, int32_t, int32_t &) const override { return -1; }
    int32_t onFind(DocId, int32_t) const override { return -1; }
    unsigned int approximateHits() const override { return 0; }
    std::unique_ptr<SearchIterator> createIterator(fef::TermFieldMatchData *, bool) override { abort(); }
    void fetchPostings(bool) override { }
    bool valid() const override { return true; }
    search::Int64Range getAsIntegerTerm() const override { abort(); }
    const search::QueryTermBase &queryTerm() const override { abort(); }
    const vespalib::string &attributeName() const override { abort(); }
};

} // namespace search::queryeval::<unnamed>

void
FakeSearch::is_attr(bool value)
{
    if (value) {
        _ctx = std::make_unique<FakeContext>();
    } else {
        _ctx.reset();
    }
}

void
FakeSearch::doSeek(uint32_t docid)
{
    while (valid() && docid > currId()) {
        next();
    }
    if (valid()) {
        setDocId(currId());
    } else {
        setAtEnd();
    }
}

void
FakeSearch::doUnpack(uint32_t docid)
{
    typedef fef::TermFieldMatchDataPosition PosCtx;
    typedef FakeResult::Document Doc;
    typedef FakeResult::Element Elem;

    assert(valid());
    const Doc &doc = _result.inspect()[_offset];
    assert(doc.docId == docid);
    _tfmda[0]->reset(docid);
    for (uint32_t i = 0; i < doc.elements.size(); ++i) {
        const Elem &elem =doc.elements[i];
        for (uint32_t j = 0; j < elem.positions.size(); ++j) {
            _tfmda[0]->appendPosition(PosCtx(elem.id, elem.positions[j],
                                             elem.weight, elem.length));
        }
    }
}

void
FakeSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "tag",   _tag);
    visit(visitor, "field", _field);
    visit(visitor, "term",  _term);
}

} // namespace search::queryeval
} // namespace search
