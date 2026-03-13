// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_search.h"
#include "element_id_extractor.h"
#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <cassert>

namespace search::queryeval {

FakeSearch::FakeSearch(const std::string &tag, const std::string &field,
                       const std::string &term, const FakeResult &res,
                       fef::TermFieldMatchDataArray tfmda)
    : _tag(tag), _field(field), _term(term),
      _result(res), _offset(0), _unpacked_docid(beginId()), _tfmda(std::move(tfmda)),
      _ctx(nullptr)
{
    assert(_tfmda.size() == 1);
}

FakeSearch::~FakeSearch() = default;

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
    _tfmda[0]->clear_hidden_from_ranking();
    if (_unpacked_docid == docid) {
        return;
    }
    using PosCtx = fef::TermFieldMatchDataPosition;
    using Doc = FakeResult::Document;
    using Elem = FakeResult::Element;

    assert(valid());
    const Doc &doc = _result.inspect()[_offset];
    assert(doc.docId == docid);
    _tfmda[0]->reset(docid);
    int32_t sum_weight = 0;
    for (uint32_t i = 0; i < doc.elements.size(); ++i) {
        const Elem &elem = doc.elements[i];
        sum_weight += elem.weight;
        if (!is_attr()) {
            for (uint32_t j = 0; j < elem.positions.size(); ++j) {
                _tfmda[0]->appendPosition(PosCtx(elem.id, elem.positions[j],
                                elem.weight, elem.length));
            }
        }
    }
    if (is_attr()) {
        _tfmda[0]->appendPosition(PosCtx(0, 0, sum_weight, 1));
    }
    if (_tfmda[0]->needs_interleaved_features()) {
        _tfmda[0]->setNumOccs(doc.num_occs);
        _tfmda[0]->setFieldLength(doc.field_length);
    }
    _unpacked_docid = docid;
}

void
FakeSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "tag",   _tag);
    visit(visitor, "field", _field);
    visit(visitor, "term",  _term);
}

void
FakeSearch::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    if (_ctx != nullptr) {
        _ctx->get_element_ids(docid, element_ids);
    } else {
        unpack(docid);
        ElementIdExtractor::get_element_ids(*_tfmda[0], docid, element_ids);
    }
}

void
FakeSearch::and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    if (_ctx != nullptr) {
        _ctx->and_element_ids_into(docid, element_ids);
    } else {
        unpack(docid);
        ElementIdExtractor::and_element_ids_into(*_tfmda[0], docid, element_ids);
    }
}

}
