// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fs4hit.h"
#include "vdshit.h"
#include <vespa/searchlib/common/identifiable.h>

namespace search::aggregation {


class HitList : public expression::ResultNode
{
public:
private:
    using ResultNode = expression::ResultNode;
    using Fs4V = std::vector<FS4Hit>;
    using VdsV = std::vector<VdsHit>;
    std::vector<FS4Hit>         _fs4hits;
    std::vector<VdsHit>         _vdshits;

    int64_t onGetInteger(size_t index) const override { (void) index; return 0; }
    double onGetFloat(size_t index)    const override { (void) index; return 0.0; }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override { (void) index; return buf; }
    size_t hash() const override { return 0; }
    void set(const ResultNode & rhs) override;
    void decode(const void * buf) override {
        _fs4hits = *static_cast<const Fs4V *>(buf);
        _vdshits = *static_cast<const VdsV *>(static_cast<const void *>(static_cast<const uint8_t *>(buf)+sizeof(_fs4hits)));
    }
    void swap(void * buf) override {
        static_cast<Fs4V *>(buf)->swap(_fs4hits);
        static_cast<VdsV *>(static_cast<void *>(static_cast<uint8_t *>(buf)+sizeof(_fs4hits)))->swap(_vdshits);
    }
    void encode(void * buf) const override {
        *static_cast<Fs4V *>(buf) = _fs4hits;
        *static_cast<VdsV *>(static_cast<void *>(static_cast<uint8_t *>(buf)+sizeof(_fs4hits))) = _vdshits;
    }
    void create(void * buf) const override {
        new (buf) Fs4V();
        new (static_cast<uint8_t *>(buf)+sizeof(_fs4hits)) VdsV();
    }
    void destroy(void * buf) const override {
        static_cast<Fs4V *>(buf)->Fs4V::~Fs4V();
        static_cast<VdsV *>(static_cast<void *>(static_cast<uint8_t *>(buf)+sizeof(_fs4hits)))->VdsV::~VdsV();
    }
    size_t getRawByteSize() const override { return sizeof(_fs4hits) + sizeof(_vdshits); }
public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, HitList);
    HitList * clone() const override { return new HitList(*this); }
    DECLARE_NBO_SERIALIZE;
    HitList() :
        _fs4hits(),
        _vdshits()
    {}
    uint32_t size() const { return (_fs4hits.size() + _vdshits.size()); }
    bool empty() const { return (_vdshits.empty() && _fs4hits.empty()); }
    const Hit & front() const { return ((_fs4hits.size() > 0) ?  (static_cast<const Hit &>(_fs4hits[0])) : (static_cast<const Hit &>(_vdshits[0]))); }

    void postMerge(uint32_t maxHits);
    void onMerge(const HitList & b);
    void clear();

    HitList & addHit(const FS4Hit & hit, uint32_t maxHits);
    HitList & addHit(const VdsHit & hit, uint32_t maxHits);
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate &predicate,
                       vespalib::ObjectOperation &operation) override;
    void sort() override;
    HitList & sort2() { sort(); return *this; }
};

}
