// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "fake_result.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchcommon/attribute/i_search_context.h>

namespace search::queryeval {

class FakeSearch : public SearchIterator
{
private:
    vespalib::string             _tag;
    vespalib::string             _field;
    vespalib::string             _term;
    FakeResult                   _result;
    uint32_t                     _offset;
    fef::TermFieldMatchDataArray _tfmda;
    const attribute::ISearchContext *_ctx;

    bool valid() const { return _offset < _result.inspect().size(); }
    uint32_t currId() const { return _result.inspect()[_offset].docId; }
    void next() { ++_offset; }

public:
    FakeSearch(const vespalib::string &tag,
               const vespalib::string &field,
               const vespalib::string &term,
               const FakeResult &res,
               fef::TermFieldMatchDataArray tfmda);
    void attr_ctx(const attribute::ISearchContext *ctx) { _ctx = ctx; }
    bool is_attr() const { return (_ctx != nullptr); }
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        _offset = 0;
    }
    const PostingInfo *getPostingInfo() const override { return _result.postingInfo(); }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
};

}
