// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attrvector.h"
#include "load_utils.h"
#include "numeric_sort_blob_writer.h"
#include "string_sort_blob_writer.h"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchlib/util/filekit.h>

namespace search {

template <typename B>
NumericDirectAttribute<B>::
NumericDirectAttribute(const vespalib::string & baseFileName, const Config & c)
    : B(baseFileName, c),
      _data(),
      _idx()
{
}

template <typename B>
NumericDirectAttribute<B>::~NumericDirectAttribute() = default;

template <typename B>
bool NumericDirectAttribute<B>::findEnum(typename B::BaseType key, EnumHandle & e) const
{
    if (_data.empty()) {
        e = 0;
        return false;
    }
    int delta;
    const int eMax = B::getEnumMax();
    for (delta = 1; delta <= eMax; delta <<= 1) { }
    delta >>= 1;
    int pos = delta - 1;
    typename B::BaseType value = key;

    while (delta != 0) {
        delta >>= 1;
        if (pos >= eMax) {
            pos -= delta;
        } else {
            value = _data[pos];
            if (value == key) {
                e = pos;
                return true;
            } else if (value < key) {
                pos += delta;
            } else {
                pos -= delta;
            }
        }
    }
    e = ((key > value) && (pos < eMax)) ? pos + 1 : pos;
    return false;
}

template <typename B>
void NumericDirectAttribute<B>::onCommit()
{
    B::_changes.clear();
    HDR_ABORT("should not be reached");
}

template <typename B>
bool NumericDirectAttribute<B>::addDoc(DocId & )
{
    return false;
}

}

template <typename F, typename B>
NumericDirectAttrVector<F, B>::
NumericDirectAttrVector(const vespalib::string & baseFileName, const AttributeVector::Config & c)
    : search::NumericDirectAttribute<B>(baseFileName, c)
{
    if (F::IsMultiValue()) {
        this->_idx.push_back(0);
    }
}

template <typename F, typename B>
NumericDirectAttrVector<F, B>::
NumericDirectAttrVector(const vespalib::string & baseFileName)
    : search::NumericDirectAttribute<B>(baseFileName, AttributeVector::Config(AttributeVector::BasicType::fromType(BaseType()), F::IsMultiValue() ? search::attribute::CollectionType::ARRAY : search::attribute::CollectionType::SINGLE))
{
    if (F::IsMultiValue()) {
        this->_idx.push_back(0);
    }
}

template <typename F, typename B>
template <bool asc>
long
NumericDirectAttrVector<F, B>::on_serialize_for_sort(DocId doc, void* serTo, long available) const
{
    search::attribute::NumericSortBlobWriter<BaseType, asc> writer;
    vespalib::ConstArrayRef<BaseType> values(this->_data.data() + this->_idx[doc], this->_idx[doc + 1] - this->_idx[doc]);
    for (auto& v : values) {
        writer.candidate(v);
    }
    return writer.write(serTo, available);
}

template <typename F, typename B>
long
NumericDirectAttrVector<F, B>::onSerializeForAscendingSort(DocId doc, void* serTo, long available, const search::common::BlobConverter* bc) const
{
    if (!F::IsMultiValue()) {
        return search::NumericDirectAttribute<B>::onSerializeForAscendingSort(doc, serTo, available, bc);
    }
    return on_serialize_for_sort<true>(doc, serTo, available);
}

template <typename F, typename B>
long
NumericDirectAttrVector<F, B>::onSerializeForDescendingSort(DocId doc, void* serTo, long available, const search::common::BlobConverter* bc) const
{
    if (!F::IsMultiValue()) {
        return search::NumericDirectAttribute<B>::onSerializeForDescendingSort(doc, serTo, available, bc);
    }
    return on_serialize_for_sort<false>(doc, serTo, available);
}

template <typename F>
StringDirectAttrVector<F>::
StringDirectAttrVector(const vespalib::string & baseFileName, const Config & c) :
    search::StringDirectAttribute(baseFileName, c)
{
    if (F::IsMultiValue()) {
        _idx.push_back(0);
    }
    setEnum(true);
}

template <typename F>
StringDirectAttrVector<F>::
StringDirectAttrVector(const vespalib::string & baseFileName) :
    search::StringDirectAttribute(baseFileName, Config(BasicType::STRING, F::IsMultiValue() ? search::attribute::CollectionType::ARRAY : search::attribute::CollectionType::SINGLE))
{
    if (F::IsMultiValue()) {
        _idx.push_back(0);
    }
    setEnum(true);
}

template <typename F>
long
StringDirectAttrVector<F>::on_serialize_for_sort(DocId doc, void* serTo, long available, const search::common::BlobConverter* bc, bool asc) const
{
    search::attribute::StringSortBlobWriter writer(serTo, available, bc, asc);
    vespalib::ConstArrayRef<uint32_t> offsets(this->_offsets.data() + this->_idx[doc], this->_idx[doc + 1] - this->_idx[doc]);
    for (auto& offset : offsets) {
        if (!writer.candidate(&this->_buffer[offset])) {
            return -1;
        }
    }
    return writer.write();
}

template <typename F>
long
StringDirectAttrVector<F>::onSerializeForAscendingSort(DocId doc, void* serTo, long available, const search::common::BlobConverter* bc) const
{
    if (!F::IsMultiValue()) {
        return search::StringDirectAttribute::onSerializeForAscendingSort(doc, serTo, available, bc);
    }
    return on_serialize_for_sort(doc, serTo, available, bc, true);
}

template <typename F>
long
StringDirectAttrVector<F>::onSerializeForDescendingSort(DocId doc, void* serTo, long available, const search::common::BlobConverter* bc) const
{
    if (!F::IsMultiValue()) {
        return search::StringDirectAttribute::onSerializeForDescendingSort(doc, serTo, available, bc);
    }
    return on_serialize_for_sort(doc, serTo, available, bc, false);
}
