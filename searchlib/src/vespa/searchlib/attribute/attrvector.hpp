// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attrvector.h"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/fastlib/io/bufferedfile.h>
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
NumericDirectAttribute<B>::~NumericDirectAttribute() {}

template <typename B>
bool NumericDirectAttribute<B>::onLoad()
{
    fileutil::LoadedBuffer::UP dataBuffer(B::loadDAT());
    bool rc(dataBuffer.get());
    if (rc) {
        const BaseType * tmpData(static_cast <const BaseType *>(dataBuffer->buffer()));
        size_t tmpDataLen(dataBuffer->size(sizeof(BaseType)));
        if (this->hasEnum() ) {
            std::vector<BaseType> tmp;
            tmp.reserve(tmpDataLen);
            for(size_t i(0); i < tmpDataLen; i++) {
                tmp.push_back(tmpData[i]);
            }
            std::sort(tmp.begin(), tmp.end());
            _data.clear();
            if (!tmp.empty()) {
                BaseType prev = tmp[0];
                _data.push_back(prev);
                for(typename std::vector<BaseType>::const_iterator it(tmp.begin()+1), mt(tmp.end()); it != mt; it++) {
                    if (*it != prev) {
                        prev = *it;
                        _data.push_back(prev);
                    }
                }
            }
            this->setEnumMax(_data.size());
        } else {
            _data.clear();
            _data.reserve(tmpDataLen);
            for (size_t i=0; i < tmpDataLen; i++) {
                _data.push_back(tmpData[i]);
            }
        }
        dataBuffer.reset();
        if (this->hasMultiValue()) {
            fileutil::LoadedBuffer::UP idxBuffer(B::loadIDX());
            rc = idxBuffer.get();
            if (rc) {
                const uint32_t * tmpIdx(static_cast<const uint32_t *>(idxBuffer->buffer()));
                size_t tmpIdxLen(idxBuffer->size(sizeof(uint32_t)));
                _idx.clear();
                _idx.reserve(tmpIdxLen);
                uint32_t prev(0);
                for (size_t i=0; i < tmpIdxLen; i++) {
                    this->checkSetMaxValueCount(tmpIdx[i] - prev);
                    prev = tmpIdx[i];
                    _idx.push_back(prev);
                }
            }
        }
    }
    if (rc) {
        uint32_t numDocs(this->hasMultiValue() ? (_idx.size() - 1) : _data.size());
        this->setNumDocs(numDocs);
        this->setCommittedDocIdLimit(numDocs);
    } else {
        std::vector<BaseType> emptyData;
        std::vector<uint32_t> empty1;
        std::vector<uint32_t> empty2;
        std::swap(emptyData, _data);
        std::swap(empty2, _idx);
    }

    // update statistics
    uint64_t numValues = _data.size();
    uint64_t numUniqueValues = _data.size();
    uint64_t allocated = _data.size() * sizeof(BaseType) + _idx.size() * sizeof(uint32_t);
    this->updateStatistics(numValues, numUniqueValues, allocated, allocated, 0, 0);

    return rc;
}

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

template <typename F>
StringDirectAttrVector<F>::
StringDirectAttrVector(const vespalib::string & baseFileName, const Config & c) :
    search::StringDirectAttribute(baseFileName, c)
{
    if (F::IsMultiValue()) {
        _idx.push_back(0);
    }
    setEnum();
}

template <typename F>
StringDirectAttrVector<F>::
StringDirectAttrVector(const vespalib::string & baseFileName) :
    search::StringDirectAttribute(baseFileName, Config(BasicType::STRING, F::IsMultiValue() ? search::attribute::CollectionType::ARRAY : search::attribute::CollectionType::SINGLE))
{
    if (F::IsMultiValue()) {
        _idx.push_back(0);
    }
    setEnum();
}

