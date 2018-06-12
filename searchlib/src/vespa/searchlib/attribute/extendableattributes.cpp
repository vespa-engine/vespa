// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.extendable_attributes");

#include "extendableattributes.h"
#include "attrvector.hpp"

namespace search {

//******************** CollectionType::SINGLE ********************//


SingleStringExtAttribute::SingleStringExtAttribute(const vespalib::string & name) :
    StringDirectAttrVector< AttrVector::Features<false> >(name, Config(BasicType::STRING, CollectionType::SINGLE))
{
    setEnum(false);
}

bool SingleStringExtAttribute::addDoc(DocId & docId)
{
    size_t offset(_buffer.size());
    _buffer.push_back('\0');
    _buffer.push_back(0);
    docId = _offsets.size();
    _offsets.push_back(offset);
    incNumDocs();
    setCommittedDocIdLimit(getNumDocs());
    return true;
}

bool SingleStringExtAttribute::add(const char * v, int32_t)
{
    const size_t start(_offsets.back());
    const size_t sz(strlen(v) + 1);
    _buffer.resize(start+sz);
    strcpy(&_buffer[start], v);
    return true;
}


//******************** CollectionType::ARRAY ********************//


MultiStringExtAttribute::MultiStringExtAttribute(const vespalib::string & name, const CollectionType & ctype) :
    StringDirectAttrVector< AttrVector::Features<true> >
    (name,  Config(BasicType::STRING, ctype))
{
    setEnum(false);
}

MultiStringExtAttribute::MultiStringExtAttribute(const vespalib::string & name) :
    StringDirectAttrVector< AttrVector::Features<true> >
    (name,  Config(BasicType::STRING, CollectionType::ARRAY))
{
    setEnum(false);
}

bool MultiStringExtAttribute::addDoc(DocId & docId)
{
    docId = _idx.size() - 1;
    _idx.push_back(_idx.back());
    incNumDocs();
    setCommittedDocIdLimit(getNumDocs());
    return true;
}

bool MultiStringExtAttribute::add(const char * v, int32_t)
{
    const size_t start(_buffer.size());
    const size_t sz(strlen(v) + 1);
    _buffer.resize(start+sz);
    strcpy(&_buffer[start], v);

    _offsets.push_back(start);

    _idx.back()++;
    checkSetMaxValueCount(_idx.back() - _idx[_idx.size() - 2]);
    return true;
}


//******************** CollectionType::WSET ********************//

WeightedSetIntegerExtAttribute::WeightedSetIntegerExtAttribute(const vespalib::string & name) :
    WeightedSetExtAttributeBase<MultiIntegerExtAttribute>(name)
{
}

WeightedSetIntegerExtAttribute::~WeightedSetIntegerExtAttribute() {}

bool
WeightedSetIntegerExtAttribute::add(int64_t v, int32_t w)
{
    addWeight(w);
    MultiIntegerExtAttribute::add(v);
    return true;
}

uint32_t
WeightedSetIntegerExtAttribute::get(DocId doc, AttributeVector::WeightedInt * v, uint32_t sz) const
{
    uint32_t valueCount = _idx[doc + 1] - _idx[doc];
    uint32_t num2Read = std::min(valueCount, sz);
    for (uint32_t i = 0; i < num2Read; ++i) {
        v[i] = AttributeVector::WeightedInt(_data[_idx[doc] + i], getWeightHelper(doc, i));
    }
    return valueCount;
}

WeightedSetFloatExtAttribute::WeightedSetFloatExtAttribute(const vespalib::string & name) :
    WeightedSetExtAttributeBase<MultiFloatExtAttribute>(name)
{
}

WeightedSetFloatExtAttribute::~WeightedSetFloatExtAttribute() {}

bool
WeightedSetFloatExtAttribute::add(double v, int32_t w)
{
    addWeight(w);
    MultiFloatExtAttribute::add(v);
    return true;
}

uint32_t
WeightedSetFloatExtAttribute::get(DocId doc, AttributeVector::WeightedFloat * v, uint32_t sz) const
{
    uint32_t valueCount = _idx[doc + 1] - _idx[doc];
    uint32_t num2Read = std::min(valueCount, sz);
    for (uint32_t i = 0; i < num2Read; ++i) {
        v[i] = AttributeVector::WeightedFloat(_data[_idx[doc] + i], getWeightHelper(doc, i));
    }
    return valueCount;
}

WeightedSetStringExtAttribute::WeightedSetStringExtAttribute(const vespalib::string & name) :
    WeightedSetExtAttributeBase<MultiStringExtAttribute>(name)
{
    setEnum(false);
}

WeightedSetStringExtAttribute::~WeightedSetStringExtAttribute() {}

bool
WeightedSetStringExtAttribute::add(const char * v, int32_t w)
{
    addWeight(w);
    MultiStringExtAttribute::add(v);
    return true;
}

uint32_t
WeightedSetStringExtAttribute::get(DocId doc, AttributeVector::WeightedString * v, uint32_t sz) const
{
    return getAllHelper(doc, v, sz);
}

uint32_t
WeightedSetStringExtAttribute::get(DocId doc, AttributeVector::WeightedConstChar * v, uint32_t sz) const
{
    return getAllHelper(doc, v, sz);
}

}
