// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_search.h"
#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.h>

namespace search {
namespace queryeval {

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

} // namespace queryeval
} // namespace search
