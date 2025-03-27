// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attrvector.h"
#include "load_utils.h"
#include "numeric_sort_blob_writer.h"
#include "string_sort_blob_writer.h"
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/util/hdr_abort.h>

namespace search {

template <typename B>
NumericDirectAttribute<B>::
NumericDirectAttribute(const std::string & baseFileName, const Config & c)
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
NumericDirectAttrVector(const std::string & baseFileName, const AttributeVector::Config & c)
    : search::NumericDirectAttribute<B>(baseFileName, c)
{
    if (F::IsMultiValue()) {
        this->_idx.push_back(0);
    }
}

template <typename F, typename B>
NumericDirectAttrVector<F, B>::
NumericDirectAttrVector(const std::string & baseFileName)
    : search::NumericDirectAttribute<B>(baseFileName, AttributeVector::Config(AttributeVector::BasicType::fromType(BaseType()), F::IsMultiValue() ? search::attribute::CollectionType::ARRAY : search::attribute::CollectionType::SINGLE))
{
    if (F::IsMultiValue()) {
        this->_idx.push_back(0);
    }
}

template <typename F, typename B>
bool
NumericDirectAttrVector<F, B>::is_sortable() const noexcept
{
    return true;
}

template <typename BaseType, bool ascending>
class NumericDirectSortBlobWriter : public search::attribute::ISortBlobWriter {
private:
    const std::vector<BaseType>& _data;
    const std::vector<uint32_t>& _idx;
public:
    NumericDirectSortBlobWriter(const std::vector<BaseType>& data, const std::vector<uint32_t>& idx) noexcept
        : _data(data), _idx(idx) {}
    long write(uint32_t docid, void* buf, long available) const override {
        search::attribute::NumericSortBlobWriter<BaseType, ascending> writer;
        std::span<const BaseType> values(_data.data() + _idx[docid], _idx[docid + 1] - _idx[docid]);
        for (auto& v : values) {
            writer.candidate(v);
        }
        return writer.write(buf, available);
    }
};

template <typename F, typename B>
std::unique_ptr<search::attribute::ISortBlobWriter>
NumericDirectAttrVector<F, B>::make_sort_blob_writer(bool ascending, const search::common::BlobConverter* converter) const
{
    if (!F::IsMultiValue()) {
        return search::NumericDirectAttribute<B>::make_sort_blob_writer(ascending, converter);
    }
    if (ascending) {
        return std::make_unique<NumericDirectSortBlobWriter<BaseType, true>>(this->_data, this->_idx);
    } else {
        return std::make_unique<NumericDirectSortBlobWriter<BaseType, false>>(this->_data, this->_idx);
    }
}

template <typename F>
StringDirectAttrVector<F>::
StringDirectAttrVector(const std::string & baseFileName, const Config & c) :
    search::StringDirectAttribute(baseFileName, c)
{
    if (F::IsMultiValue()) {
        _idx.push_back(0);
    }
    setEnum(true);
}

template <typename F>
StringDirectAttrVector<F>::
StringDirectAttrVector(const std::string & baseFileName) :
    search::StringDirectAttribute(baseFileName, Config(BasicType::STRING, F::IsMultiValue() ? search::attribute::CollectionType::ARRAY : search::attribute::CollectionType::SINGLE))
{
    if (F::IsMultiValue()) {
        _idx.push_back(0);
    }
    setEnum(true);
}

class StringDirectSortBlobWriter : public search::attribute::ISortBlobWriter {
private:
    const std::vector<char>& _buffer;
    const search::StringAttribute::OffsetVector& _offsets;
    const std::vector<uint32_t>& _idx;
    const search::common::BlobConverter* _converter;
    bool _ascending;
public:
    StringDirectSortBlobWriter(const std::vector<char>& buffer, const search::StringAttribute::OffsetVector& offsets,
                               const std::vector<uint32_t>& idx, const search::common::BlobConverter* converter,
                               bool ascending)
        : _buffer(buffer), _offsets(offsets), _idx(idx), _converter(converter), _ascending(ascending) {}
    long write(uint32_t docid, void* buf, long available) const override {
        search::attribute::StringSortBlobWriter writer(buf, available, _converter, _ascending);
        std::span<const uint32_t> offsets(_offsets.data() + _idx[docid], _idx[docid + 1] - _idx[docid]);
        for (auto& offset : offsets) {
            if (!writer.candidate(&_buffer[offset])) {
                return -1;
            }
        }
        return writer.write();
    }
};

template <typename F>
bool
StringDirectAttrVector<F>::is_sortable() const noexcept
{
    return true;
}

template <typename F>
std::unique_ptr<search::attribute::ISortBlobWriter>
StringDirectAttrVector<F>::make_sort_blob_writer(bool ascending, const search::common::BlobConverter* converter) const
{
    if (!F::IsMultiValue()) {
        return search::StringDirectAttribute::make_sort_blob_writer(ascending, converter);
    }
    return std::make_unique<StringDirectSortBlobWriter>(this->_buffer, this->_offsets, this->_idx, converter, ascending);
}
