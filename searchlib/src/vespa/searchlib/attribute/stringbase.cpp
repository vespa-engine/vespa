// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringbase.h"
#include "attributevector.hpp"
#include "readerbase.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/searchlib/util/fileutil.hpp>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/array.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.stringbase");

namespace search {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(StringAttribute, AttributeVector);

using attribute::LoadedEnumAttribute;
using attribute::LoadedEnumAttributeVector;

AttributeVector::SearchContext::UP
StringAttribute::getSearch(QueryTermSimple::UP term, const attribute::SearchContextParams & params) const
{
    (void) params;
    return SearchContext::UP(new StringSearchContext(std::move(term), *this));
}

class SortDataChar {
public:
    SortDataChar() { }
    SortDataChar(const char *s) : _data(s), _pos(0) { }
    operator const char * () const { return _data; }
    bool operator != (const vespalib::string & b) const { return b != _data; }
    const char * _data;
    uint32_t     _pos;
};

class SortDataCharRadix
{
public:
    uint32_t operator () (SortDataChar & a) const {
        uint32_t r(0);
        const uint8_t *u((const uint8_t *)(a._data));
        if (u[a._pos]) {
            r |= u[a._pos + 0] << 24;
            if (u[a._pos + 1]) {
                r |= u[a._pos + 1] << 16;
                if (u[a._pos + 2]) {
                    r |= u[a._pos + 2] << 8;
                    if (u[a._pos + 3]) {
                        r |= u[a._pos + 3];
                        a._pos += 4;
                    } else {
                        a._pos += 3;
                    }
                } else {
                    a._pos += 2;
                }
            } else {
                a._pos += 1;
            }
        }
        return r;
    }
};

class StdSortDataCharCompare : public std::binary_function<SortDataChar, SortDataChar, bool>
{
public:
    bool operator() (const SortDataChar & x, const SortDataChar & y) const {
        return cmp(x, y) < 0;
    }
    int cmp(const SortDataChar & a, const SortDataChar & b) const {
        int retval = strcmp(a._data, b._data);
        return retval;
    }
};

class SortDataCharEof
{
public:
    bool operator () (const SortDataChar & a) const { return a._data[a._pos] == 0; }
    static bool alwaysEofOnCheck() { return false; }
};

class StringSorter {
public:
    typedef const char * constcharp;
    void operator() (SortDataChar * start, size_t sz) const {
        vespalib::Array<uint32_t> radixScratchPad(sz);
        search::radix_sort(SortDataCharRadix(), StdSortDataCharCompare(), SortDataCharEof(), 1, start, sz, &radixScratchPad[0], 0, 32);
    }
};

size_t StringAttribute::countZero(const char * bt, size_t sz)
{
    size_t size(0);
    for(size_t i(0); i < sz; i++) {
        if (bt[i] == '\0') {
            size++;
        }
    }
    return size;
}

void StringAttribute::generateOffsets(const char * bt, size_t sz, OffsetVector & offsets)
{
    offsets.clear();
    uint32_t start(0);
    for (size_t i(0); i < sz; i++) {
        if (bt[i] == '\0') {
            offsets.push_back(start);
            start = i + 1;
        }
    }
}

StringAttribute::StringAttribute(const vespalib::string & name) :
    AttributeVector(name, Config(BasicType::STRING)),
    _changes(),
    _defaultValue(ChangeBase::UPDATE, 0, vespalib::string(""))
{
}

StringAttribute::StringAttribute(const vespalib::string & name, const Config & c) :
    AttributeVector(name, c),
    _changes(),
    _defaultValue(ChangeBase::UPDATE, 0, vespalib::string(""))
{
}

StringAttribute::~StringAttribute() {}

uint32_t StringAttribute::get(DocId doc, WeightedInt * v, uint32_t sz) const
{
    WeightedConstChar * s = new WeightedConstChar[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = WeightedInt(strtoll(s[i].getValue(), NULL, 0), s[i].getWeight());
    }
    delete [] s;
    return n;
}

uint32_t StringAttribute::get(DocId doc, WeightedFloat * v, uint32_t sz) const
{
    WeightedConstChar * s = new WeightedConstChar[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = WeightedFloat(vespalib::locale::c::strtod(s[i].getValue(), NULL), s[i].getWeight());
    }
    delete [] s;
    return n;
}

double
StringAttribute::getFloat(DocId doc) const {
    return vespalib::locale::c::strtod(get(doc), NULL);
}

uint32_t StringAttribute::get(DocId doc, double * v, uint32_t sz) const
{
    const char ** s = new const char *[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = vespalib::locale::c::strtod(s[i], NULL);
    }
    delete [] s;
    return n;
}

uint32_t StringAttribute::get(DocId doc, largeint_t * v, uint32_t sz) const
{
    const char ** s = new const char *[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = strtoll(s[i], NULL, 0);
    }
    delete [] s;
    return n;
}

long StringAttribute::onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    unsigned char *dst = static_cast<unsigned char *>(serTo);
    const char *value(get(doc));
    int size = strlen(value) + 1;
    vespalib::ConstBufferRef buf(value, size);
    if (bc != 0) {
        buf = bc->convert(buf);
    }
    if (available >= (long)buf.size()) {
        memcpy(dst, buf.data(), buf.size());
    } else {
        return -1;
    }
    return buf.size();
}

long StringAttribute::onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    (void) bc;
    unsigned char *dst = static_cast<unsigned char *>(serTo);
    const char *value(get(doc));
    int size = strlen(value) + 1;
    vespalib::ConstBufferRef buf(value, size);
    if (bc != 0) {
        buf = bc->convert(buf);
    }
    if (available >= (long)buf.size()) {
        const uint8_t * src(static_cast<const uint8_t *>(buf.data()));
        for (size_t i(0), m(buf.size()); i < m; ++i) {
            dst[i] = 0xff - src[i];
        }
    } else {
        return -1;
    }
    return buf.size();
}

StringAttribute::StringSearchContext::StringSearchContext(QueryTermSimple::UP qTerm,
                                                          const StringAttribute & toBeSearched) :
    SearchContext(toBeSearched),
    _isPrefix(qTerm->isPrefix()),
    _isRegex(qTerm->isRegex()),
    _queryTerm(std::move(qTerm)),
    _termUCS4(queryTerm()->getUCS4Term()),
    _bufferLen(toBeSearched.getMaxValueCount()),
    _buffer(nullptr),
    _regex()
{
    if (isRegex()) {
        try {
            _regex = std::regex(_queryTerm->getTerm(), std::regex::icase);
        } catch (std::regex_error &) {
        }
    }
}

StringAttribute::StringSearchContext::~StringSearchContext()
{
    if (_buffer != nullptr) {
        delete [] _buffer;
    }
}

bool
StringAttribute::StringSearchContext::valid() const
{
    return (_queryTerm.get() && (!_queryTerm->empty()));
}

const QueryTermUCS4 *
StringAttribute::StringSearchContext::queryTerm() const
{
    return static_cast<const QueryTermUCS4 *>(_queryTerm.get());
}

uint32_t StringAttribute::clearDoc(DocId doc)
{
    uint32_t removed(0);
    if (hasMultiValue() && (doc < getNumDocs())) {
        removed = getValueCount(doc);
    }
    AttributeVector::clearDoc(_changes, doc);

    return removed;
}

namespace {

class DirectAccessor {
public:
    DirectAccessor() { }
    const char * get(const char * v) const { return v; }
};

}

int32_t
StringAttribute::StringSearchContext::onFind(DocId docId, int32_t elemId, int32_t &weight) const
{
    WeightedConstChar * buffer = getBuffer();
    uint32_t valueCount = attribute().get(docId, buffer, _bufferLen);

    CollectWeight collector;
    DirectAccessor accessor;
    int32_t foundElem = findNextMatch(vespalib::ConstArrayRef<WeightedConstChar>(buffer, std::min(valueCount, _bufferLen)), elemId, accessor, collector);
    weight = collector.getWeight();
    return foundElem;
}

int32_t
StringAttribute::StringSearchContext::onFind(DocId docId, int32_t elemId) const
{
    WeightedConstChar * buffer = getBuffer();
    uint32_t valueCount = attribute().get(docId, buffer, _bufferLen);
    for (uint32_t i = elemId, m = std::min(valueCount, _bufferLen); (i < m); i++) {
        if (isMatch(buffer[i].getValue())) {
            return i;
        }
    }

    return -1;
}

bool StringAttribute::applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust)
{
    vespalib::string v = fv.getAsString();
    return AttributeVector::adjustWeight(_changes, doc, StringChangeData(v), wAdjust);
}

bool StringAttribute::apply(DocId, const ArithmeticValueUpdate & )
{
    return false;
}

bool
StringAttribute::onLoadEnumerated(ReaderBase &attrReader)
{
    fileutil::LoadedBuffer::UP udatBuffer(loadUDAT());

    bool hasIdx(attrReader.hasIdx());
    size_t numDocs(0);
    uint64_t numValues(0);
    if (hasIdx) {
        numDocs = attrReader.getNumIdx() - 1;
        numValues = attrReader.getNumValues();
        uint64_t enumCount = attrReader.getEnumCount();
        assert(numValues == enumCount);
        (void) enumCount;
    } else {
        numValues = attrReader.getEnumCount();
        numDocs = numValues;
    }

    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);

    if (hasPostings()) {
        auto loader = this->getEnumStoreBase()->make_enumerated_postings_loader();
        loader.load_unique_values(udatBuffer->buffer(), udatBuffer->size());
        load_enumerated_data(attrReader, loader, numValues);
        if (numDocs > 0) {
            onAddDoc(numDocs - 1);
        }
        load_posting_lists_and_update_enum_store(loader);
    } else {
        auto loader = this->getEnumStoreBase()->make_enumerated_loader();
        loader.load_unique_values(udatBuffer->buffer(), udatBuffer->size());
        load_enumerated_data(attrReader, loader);
    }
    return true;
}

bool StringAttribute::onLoad()
{
    ReaderBase attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok) {
        return false;
    }

    setCreateSerialNum(attrReader.getCreateSerialNum());

    assert(attrReader.getEnumerated());
    return onLoadEnumerated(attrReader);
}

bool
StringAttribute::onAddDoc(DocId )
{
    return false;
}

void StringAttribute::load_posting_lists(LoadedVector&)
{
}

void StringAttribute::load_enum_store(LoadedVector&)
{
}

void StringAttribute::fillValues(LoadedVector & )
{
}

void
StringAttribute::load_enumerated_data(ReaderBase&, enumstore::EnumeratedPostingsLoader&, size_t)
{
    LOG_ABORT("Should not be reached");
}

void
StringAttribute::load_enumerated_data(ReaderBase&, enumstore::EnumeratedLoader&)
{
    LOG_ABORT("Should not be reached");
}

void
StringAttribute::load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader&)
{
    LOG_ABORT("Should not be reached");
}

vespalib::MemoryUsage
StringAttribute::getChangeVectorMemoryUsage() const
{
    return _changes.getMemoryUsage();
}

}
