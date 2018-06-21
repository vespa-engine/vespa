// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attrvector.h"
#include "attrvector.hpp"
#include "iattributesavetarget.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attr_vector");

namespace search {

StringDirectAttribute::
StringDirectAttribute(const vespalib::string & baseFileName, const Config & c)
    : search::StringAttribute(baseFileName, c),
      _buffer(),
      _offsets(),
      _idx()
{
}

StringDirectAttribute::~StringDirectAttribute() {}

bool StringDirectAttribute::findEnum(const char * key, EnumHandle & e) const
{
    if (_offsets.size() < 1) {
        e = 0;
        return false;
    }
    int delta;
    const int eMax = getEnumMax();
    for (delta = 1; delta <= eMax; delta <<= 1) { }
    delta >>= 1;
    int pos = delta - 1;
    int cmpres(0);

    while (delta != 0) {
        delta >>= 1;
        if (pos >= eMax) {
            pos -= delta;
        } else {
            const char *name = &_buffer[_offsets[pos]];
            cmpres = strcmp(key, name);
            if (cmpres == 0) {
                e = pos;
                return true;
            }
            pos += (cmpres < 0) ? -delta : +delta;
        }
    }
    e = ((cmpres > 0) && (pos < eMax)) ? pos + 1 : pos;
    return false;
}

void StringDirectAttribute::onSave(IAttributeSaveTarget & saveTarget)
{
    assert(!saveTarget.getEnumerated());
    using Buffer = IAttributeSaveTarget::Buffer;
    if ( hasEnum() ) {
        uint32_t sz(getMaxValueCount());
        Buffer dat(saveTarget.datWriter().allocBuf(sz*getNumDocs()*11));
        const char * * v = new const char *[sz];
        for(size_t i(0), m(getNumDocs()); i < m; i++) {
            for(size_t j(0), k(static_cast<const AttributeVector &>(*this).get(i, v, sz)); j < k; j++) {
                dat->writeBytes(v[j], strlen(v[j]) + 1);
            }
        }
        delete [] v;
    } else if ( ! _buffer.empty() ) {
        Buffer dat(saveTarget.datWriter().allocBuf(_buffer.size()));
        dat->writeBytes(&_buffer[0], _buffer.size());
        saveTarget.datWriter().writeBuf(std::move(dat));
    }

    if (hasMultiValue()) {
        Buffer buf(saveTarget.idxWriter().allocBuf(sizeof(uint32_t) *
                                                   _idx.size()));
        buf->writeBytes(&_idx[0], sizeof(uint32_t) * _idx.size());
        saveTarget.idxWriter().writeBuf(std::move(buf));
    }
}

class stringComp : public std::binary_function<uint32_t, uint32_t, bool> {
public:
    stringComp(const char * buffer) : _buffer(buffer) { }
    bool operator()(uint32_t x, uint32_t y) const { return strcmp(_buffer+x, _buffer+y) < 0; }
private:
    const char * _buffer;
};

void addString(const char * v, StringAttribute::OffsetVector & offsets, std::vector<char> & buffer)
{
    offsets.push_back(buffer.size());
    for(const char *p(v); *p; p++) {
        buffer.push_back(*p);
    }
    buffer.push_back('\0');
}

bool StringDirectAttribute::onLoad()
{
    {
        std::vector<char> empty;
        std::vector<uint32_t> empty1;
        std::vector<uint32_t> empty2;
        OffsetVector empty3;
        std::swap(empty, _buffer);
        std::swap(empty2, _idx);
        std::swap(empty3, _offsets);
        setNumDocs(0);
        setCommittedDocIdLimit(0);
    }

    fileutil::LoadedBuffer::UP tmpBuffer(loadDAT());
    bool rc(tmpBuffer.get());
    if (rc) {
        if ( ! tmpBuffer->empty()) {
            OffsetVector tmpOffsets;
            tmpOffsets.reserve(countZero(tmpBuffer->c_str(), tmpBuffer->size()) + 1);
            generateOffsets(tmpBuffer->c_str(), tmpBuffer->size(), tmpOffsets);

            if ( hasEnum() ) {
                std::sort(tmpOffsets.begin(), tmpOffsets.end(), stringComp(tmpBuffer->c_str()));
                _offsets.clear();
                _buffer.clear();
                if (!tmpOffsets.empty()) {
                    const char * prev(tmpBuffer->c_str() + tmpOffsets[0]);
                    addString(prev, _offsets, _buffer);
                    for(OffsetVector::const_iterator it(tmpOffsets.begin()+1), mt(tmpOffsets.end()); it != mt; it++) {
                        if (strcmp(tmpBuffer->c_str() + *it, prev) != 0) {
                            prev = tmpBuffer->c_str() + *it;
                            addString(prev, _offsets, _buffer);
                        }
                    }
                }
                setEnumMax(_offsets.size());
                generateOffsets(tmpBuffer->c_str(), tmpBuffer->size(), tmpOffsets);
            } else {
                _buffer.clear();
                _buffer.reserve(tmpBuffer->size());
                for (size_t i=0; i < tmpBuffer->size(); i++) {
                    _buffer.push_back(tmpBuffer->c_str()[i]);
                }
                std::swap(tmpOffsets, _offsets);
            }
        }

        if (hasMultiValue()) {
            fileutil::LoadedBuffer::UP tmpIdx(loadIDX());
            size_t tmpIdxLen(tmpIdx->size(sizeof(uint32_t)));
            _idx.clear();
            _idx.reserve(tmpIdxLen);
            uint32_t prev(0);
            const uint32_t * idxPtr(static_cast<const uint32_t *>(tmpIdx->buffer()));
            for (size_t i=0; i < tmpIdxLen; i++) {
                checkSetMaxValueCount(idxPtr[i] - prev);
                prev = idxPtr[i];
                _idx.push_back(prev);
            }
            rc = tmpIdx.get();
            tmpIdx.reset();
        }
        uint32_t numDocs(hasMultiValue() ? (_idx.size()-1) : _offsets.size());
        setNumDocs(numDocs);
        setCommittedDocIdLimit(numDocs);
    }

    // update statistics
    uint64_t numValues = _offsets.size();
    uint64_t numUniqueValues = _offsets.size();
    uint64_t allocated = _buffer.size() * sizeof(char) + _offsets.size() * sizeof(uint32_t) +
        + _idx.size() * sizeof(uint32_t);
    this->updateStatistics(numValues, numUniqueValues, allocated, allocated, 0, 0);
    return rc;
}

void StringDirectAttribute::onCommit()
{
    LOG_ABORT("should not be reached");
}

bool StringDirectAttribute::addDoc(DocId & doc)
{
    (void) doc;
    return false;
}

}  // namespace search
