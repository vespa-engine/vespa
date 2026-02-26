// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_ext_attribute.h"
#include "search_context.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.array_bool_ext_attribute");

namespace search::attribute {

ArrayBoolExtAttribute::ArrayBoolExtAttribute(const std::string& name)
    : ArrayBoolAttributeAccess(name, Config(BasicType::BOOL, CollectionType::ARRAY)),
      _bits(),
      _idx(1, 0u)
{
}

ArrayBoolExtAttribute::~ArrayBoolExtAttribute() = default;

vespalib::BitSpan
ArrayBoolExtAttribute::get_bools(DocId docid) const
{
    if (docid + 1 >= _idx.size()) {
        return {};
    }
    uint64_t offset = _idx[docid];
    uint64_t length = _idx[docid + 1] - offset;
    return _bits.bit_span(offset, length);
}

vespalib::BitSpan
ArrayBoolExtAttribute::get_values(uint32_t docid) const
{
    return get_bools(docid);
}

bool
ArrayBoolExtAttribute::add(int64_t v, int32_t)
{
    _bits.push_back(v != 0);
    _idx.back()++;
    return true;
}

IExtendAttribute*
ArrayBoolExtAttribute::getExtendInterface()
{
    return this;
}

bool
ArrayBoolExtAttribute::addDoc(DocId& docId)
{
    docId = _idx.size() - 1;
    _idx.push_back(_idx.back());
    incNumDocs();
    setCommittedDocIdLimit(getNumDocs());
    return true;
}

void
ArrayBoolExtAttribute::onCommit()
{
}

bool
ArrayBoolExtAttribute::onLoad(vespalib::Executor*)
{
    return false;
}

void
ArrayBoolExtAttribute::onUpdateStat(CommitParam::UpdateStats)
{
}

uint32_t
ArrayBoolExtAttribute::clearDoc(DocId)
{
    return 0;
}

void
ArrayBoolExtAttribute::onAddDocs(DocId)
{
}

std::unique_ptr<attribute::SearchContext>
ArrayBoolExtAttribute::getSearch(QueryTermSimpleUP, const SearchContextParams&) const
{
    return {};
}

const IArrayBoolReadView*
ArrayBoolExtAttribute::make_read_view(ArrayBoolTag, vespalib::Stash&) const
{
    return this;
}

}
