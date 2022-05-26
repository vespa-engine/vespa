// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extendableattributes.hpp"
#include "extendable_numeric_array_multi_value_read_view.h"
#include "extendable_numeric_weighted_set_multi_value_read_view.h"
#include "extendable_string_array_multi_value_read_view.h"
#include "extendable_string_weighted_set_multi_value_read_view.h"
#include <vespa/vespalib/util/stash.h>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.extendable_attributes");

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

template <typename T>
const attribute::IMultiValueAttribute*
MultiExtAttribute<T>::as_multi_value_attribute() const
{
    return this;
}

template <typename T>
const attribute::IArrayReadView<T>*
MultiExtAttribute<T>::make_read_view(attribute::IMultiValueAttribute::ArrayTag<T>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableNumericArrayMultiValueReadView<T, T>>(this->_data, this->_idx);
}

template <typename T>
const attribute::IWeightedSetReadView<T>*
MultiExtAttribute<T>::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<T>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableNumericArrayMultiValueReadView<multivalue::WeightedValue<T>, T>>(this->_data, this->_idx);
}

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

const attribute::IMultiValueAttribute*
MultiStringExtAttribute::as_multi_value_attribute() const
{
    return this;
}

const attribute::IArrayReadView<const char*>*
MultiStringExtAttribute::make_read_view(attribute::IMultiValueAttribute::ArrayTag<const char*>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableStringArrayMultiValueReadView<const char*>>(this->_buffer, this->_offsets, this->_idx);
}

const attribute::IWeightedSetReadView<const char*>*
MultiStringExtAttribute::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<const char*>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableStringArrayMultiValueReadView<multivalue::WeightedValue<const char*>>>(this->_buffer, this->_offsets, this->_idx);
}

//******************** CollectionType::WSET ********************//

WeightedSetIntegerExtAttribute::WeightedSetIntegerExtAttribute(const vespalib::string & name) :
    WeightedSetExtAttributeBase<MultiIntegerExtAttribute>(name)
{
}

WeightedSetIntegerExtAttribute::~WeightedSetIntegerExtAttribute() = default;

std::unique_ptr<attribute::SearchContext>
WeightedSetIntegerExtAttribute::getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const
{
    (void) term;
    (void) params;
    return {};
}

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

const attribute::IWeightedSetReadView<int64_t>*
WeightedSetIntegerExtAttribute::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<int64_t>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableNumericWeightedSetMultiValueReadView<multivalue::WeightedValue<int64_t>, int64_t>>(this->_data, this->_idx, this->get_weights());
}

WeightedSetFloatExtAttribute::WeightedSetFloatExtAttribute(const vespalib::string & name) :
    WeightedSetExtAttributeBase<MultiFloatExtAttribute>(name)
{
}

WeightedSetFloatExtAttribute::~WeightedSetFloatExtAttribute() = default;

std::unique_ptr<attribute::SearchContext>
WeightedSetFloatExtAttribute::getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const
{
    (void) term;
    (void) params;
    return {};
}

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

const attribute::IWeightedSetReadView<double>*
WeightedSetFloatExtAttribute::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<double>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableNumericWeightedSetMultiValueReadView<multivalue::WeightedValue<double>, double>>(this->_data, this->_idx, this->get_weights());
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

const attribute::IWeightedSetReadView<const char*>*
WeightedSetStringExtAttribute::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<const char*>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::ExtendableStringWeightedSetMultiValueReadView<multivalue::WeightedValue<const char*>>>(this->_buffer, this->_offsets, this->_idx, this->get_weights());
}

template class MultiExtAttribute<int8_t>;
template class MultiExtAttribute<int16_t>;
template class MultiExtAttribute<int32_t>;
template class MultiExtAttribute<int64_t>;
template class MultiExtAttribute<double>;

template class SingleExtAttribute<int8_t>;
template class SingleExtAttribute<int16_t>;
template class SingleExtAttribute<int32_t>;
template class SingleExtAttribute<int64_t>;
template class SingleExtAttribute<double>;

}
