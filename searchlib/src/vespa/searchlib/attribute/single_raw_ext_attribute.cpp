// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_raw_ext_attribute.h"
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.single_raw_ext_attribute");

namespace search::attribute {

SingleRawExtAttribute::SingleRawExtAttribute(const vespalib::string& name)
    : RawAttribute(name, Config(BasicType::RAW, CollectionType::SINGLE)),
      IExtendAttribute(),
      _buffer(),
      _offsets()
{
}

SingleRawExtAttribute::~SingleRawExtAttribute() = default;

void
SingleRawExtAttribute::onCommit()
{
    LOG_ABORT("should not be reached");
}

void
SingleRawExtAttribute::onUpdateStat()
{
}

bool
SingleRawExtAttribute::addDoc(DocId& docId)
{
    size_t offset(_buffer.size());
    docId = _offsets.size();
    _offsets.push_back(offset);
    incNumDocs();
    setCommittedDocIdLimit(getNumDocs());
    return true;
}

bool
SingleRawExtAttribute::add(vespalib::ConstArrayRef<char> v, int32_t)
{
    const size_t start(_offsets.back());
    const size_t sz(v.size());
    _buffer.resize(start + sz);
    memcpy(&_buffer[start], v.data(), sz);
    return true;
}

vespalib::ConstArrayRef<char>
SingleRawExtAttribute::get_raw(DocId docid) const
{
    if (docid >= _offsets.size()) {
        return {};
    }
    auto offset = _offsets[docid];
    auto size = ((docid + 1 >= _offsets.size()) ? _buffer.size() : _offsets[docid + 1]) - offset;
    if (size == 0) {
        return {};
    }
    return {_buffer.data() + offset, size};
}

IExtendAttribute*
SingleRawExtAttribute::getExtendInterface()
{
    return this;
}

}
