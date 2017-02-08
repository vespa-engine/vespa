// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/identifiable.h>
#include "fs4hit.h"
#include "vdshit.h"

namespace search {
namespace aggregation {


class HitList : public expression::ResultNode
{
public:
private:
    using ResultNode = expression::ResultNode;
    typedef std::vector<FS4Hit> Fs4V;
    typedef std::vector<VdsHit> VdsV;
    std::vector<FS4Hit>         _fs4hits;
    std::vector<VdsHit>         _vdshits;

    virtual int64_t onGetInteger(size_t index) const { (void) index; return 0; }
    virtual double onGetFloat(size_t index)    const { (void) index; return 0.0; }
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const { (void) index; return buf; }
    virtual size_t hash() const { return 0; }
    virtual void set(const ResultNode & rhs);
    virtual void decode(const void * buf) {
        _fs4hits = *static_cast<const Fs4V *>(buf);
        _vdshits = *static_cast<const VdsV *>(static_cast<const void *>(static_cast<const uint8_t *>(buf)+sizeof(_fs4hits)));
    }
    virtual void swap(void * buf) {
        static_cast<Fs4V *>(buf)->swap(_fs4hits);
        static_cast<VdsV *>(static_cast<void *>(static_cast<uint8_t *>(buf)+sizeof(_fs4hits)))->swap(_vdshits);
    }
    virtual void encode(void * buf) const {
        *static_cast<Fs4V *>(buf) = _fs4hits;
        *static_cast<VdsV *>(static_cast<void *>(static_cast<uint8_t *>(buf)+sizeof(_fs4hits))) = _vdshits;
    }
    virtual void create(void * buf) const {
        new (buf) Fs4V();
        new (static_cast<uint8_t *>(buf)+sizeof(_fs4hits)) VdsV();
    }
    virtual void destroy(void * buf) const {
        static_cast<Fs4V *>(buf)->Fs4V::~Fs4V();
        static_cast<VdsV *>(static_cast<void *>(static_cast<uint8_t *>(buf)+sizeof(_fs4hits)))->VdsV::~VdsV();
    }
    virtual size_t getRawByteSize() const { return sizeof(_fs4hits) + sizeof(_vdshits); }
public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, HitList);
    HitList * clone() const { return new HitList(*this); }
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
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual void selectMembers(const vespalib::ObjectPredicate &predicate,
                               vespalib::ObjectOperation &operation);
    void sort();
    HitList & sort2() { sort(); return *this; }
};

}
}

