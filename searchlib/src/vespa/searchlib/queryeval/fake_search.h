// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "fake_result.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchcommon/attribute/i_search_context.h>

namespace search {
namespace queryeval {

class FakeSearch : public SearchIterator
{
private:
    vespalib::string             _tag;
    vespalib::string             _field;
    vespalib::string             _term;
    FakeResult                   _result;
    uint32_t                     _offset;
    fef::TermFieldMatchDataArray _tfmda;
    std::unique_ptr<attribute::ISearchContext> _ctx;

    bool valid() const { return _offset < _result.inspect().size(); }
    uint32_t currId() const { return _result.inspect()[_offset].docId; }
    void next() { ++_offset; }

public:
    FakeSearch(const vespalib::string &tag,
               const vespalib::string &field,
               const vespalib::string &term,
               const FakeResult &res,
               const fef::TermFieldMatchDataArray &tfmda)
        : _tag(tag), _field(field), _term(term),
          _result(res), _offset(0), _tfmda(tfmda)
    {
        assert(_tfmda.size() == 1);
    }
    void is_attr(bool value);
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    const PostingInfo *getPostingInfo() const override { return _result.postingInfo(); }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const attribute::ISearchContext *getAttributeSearchContext() const override { return _ctx.get(); }
};

} // namespace queryeval
} // namespace search
