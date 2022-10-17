// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringbase.h"
#include "attributevector.hpp"
#include "load_utils.h"
#include "readerbase.h"
#include "enum_store_loaders.h"
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/locale/c.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.stringbase");

namespace search {

size_t
StringAttribute::countZero(const char * bt, size_t sz)
{
    size_t size(0);
    for(size_t i(0); i < sz; i++) {
        if (bt[i] == '\0') {
            size++;
        }
    }
    return size;
}

void
StringAttribute::generateOffsets(const char * bt, size_t sz, OffsetVector & offsets)
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

StringAttribute::~StringAttribute() = default;

uint32_t
StringAttribute::get(DocId doc, WeightedInt * v, uint32_t sz) const
{
    auto * s = new WeightedConstChar[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = WeightedInt(strtoll(s[i].getValue(), nullptr, 0), s[i].getWeight());
    }
    delete [] s;
    return n;
}

uint32_t
StringAttribute::get(DocId doc, WeightedFloat * v, uint32_t sz) const
{
    auto * s = new WeightedConstChar[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = WeightedFloat(vespalib::locale::c::strtod(s[i].getValue(), nullptr), s[i].getWeight());
    }
    delete [] s;
    return n;
}

double
StringAttribute::getFloat(DocId doc) const {
    return vespalib::locale::c::strtod(get(doc), nullptr);
}

uint32_t
StringAttribute::get(DocId doc, double * v, uint32_t sz) const
{
    const char ** s = new const char *[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = vespalib::locale::c::strtod(s[i], nullptr);
    }
    delete [] s;
    return n;
}

uint32_t
StringAttribute::get(DocId doc, largeint_t * v, uint32_t sz) const
{
    const char ** s = new const char *[sz];
    uint32_t n = static_cast<const AttributeVector *>(this)->get(doc, s, sz);
    for(uint32_t i(0),m(std::min(n,sz)); i<m; i++) {
        v[i] = strtoll(s[i], nullptr, 0);
    }
    delete [] s;
    return n;
}

long
StringAttribute::onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    auto *dst = static_cast<unsigned char *>(serTo);
    const char *value(get(doc));
    int size = strlen(value) + 1;
    vespalib::ConstBufferRef buf(value, size);
    if (bc != nullptr) {
        buf = bc->convert(buf);
    }
    if (available >= (long)buf.size()) {
        memcpy(dst, buf.data(), buf.size());
    } else {
        return -1;
    }
    return buf.size();
}

long
StringAttribute::onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    (void) bc;
    auto *dst = static_cast<unsigned char *>(serTo);
    const char *value(get(doc));
    int size = strlen(value) + 1;
    vespalib::ConstBufferRef buf(value, size);
    if (bc != nullptr) {
        buf = bc->convert(buf);
    }
    if (available >= (long)buf.size()) {
        const auto * src(static_cast<const uint8_t *>(buf.data()));
        for (size_t i(0); i < buf.size(); ++i) {
            dst[i] = 0xff - src[i];
        }
    } else {
        return -1;
    }
    return buf.size();
}

uint32_t
StringAttribute::clearDoc(DocId doc)
{
    uint32_t removed(0);
    if (hasMultiValue() && (doc < getNumDocs())) {
        removed = getValueCount(doc);
    }
    AttributeVector::clearDoc(_changes, doc);

    return removed;
}

bool
StringAttribute::applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust)
{
    vespalib::string v = fv.getAsString();
    return AttributeVector::adjustWeight(_changes, doc, StringChangeData(v), wAdjust);
}

bool
StringAttribute::applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust)
{
    vespalib::string v = fv.getAsString();
    return AttributeVector::adjustWeight(_changes, doc, StringChangeData(v), wAdjust);
}

bool
StringAttribute::apply(DocId, const ArithmeticValueUpdate & )
{
    return false;
}

bool
StringAttribute::onLoadEnumerated(ReaderBase &attrReader)
{
    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);

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
        loader.build_enum_value_remapping();
        load_enumerated_data(attrReader, loader, numValues);
        if (numDocs > 0) {
            onAddDoc(numDocs - 1);
        }
        load_posting_lists_and_update_enum_store(loader);
    } else {
        auto loader = this->getEnumStoreBase()->make_enumerated_loader();
        loader.load_unique_values(udatBuffer->buffer(), udatBuffer->size());
        loader.build_enum_value_remapping();
        load_enumerated_data(attrReader, loader);
    }
    return true;
}

bool
StringAttribute::onLoad(vespalib::Executor *)
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

void
StringAttribute::load_posting_lists(LoadedVector&)
{
}

void
StringAttribute::load_enum_store(LoadedVector&)
{
}

void
StringAttribute::fillValues(LoadedVector & )
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

bool
StringAttribute::get_match_is_cased() const noexcept {
    return getConfig().get_match() == attribute::Config::Match::CASED;
}

template bool AttributeVector::clearDoc(StringAttribute::ChangeVector& changes, DocId doc);
template bool AttributeVector::update(StringAttribute::ChangeVector& changes, DocId doc, const StringChangeData& v);
template bool AttributeVector::append(StringAttribute::ChangeVector& changes, DocId doc, const StringChangeData& v, int32_t w, bool doCount);
template bool AttributeVector::remove(StringAttribute::ChangeVector& changes, DocId doc, const StringChangeData& v, int32_t w);

}
