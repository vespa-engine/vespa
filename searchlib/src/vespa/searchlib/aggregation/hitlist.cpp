// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitlist.h"
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <stdexcept>

namespace search::aggregation {

using vespalib::Serializer;
using vespalib::Deserializer;
using HitCP = vespalib::IdentifiablePtr<Hit>;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, HitList, ResultNode);

HitList & HitList::addHit(const FS4Hit & hit, uint32_t maxHits)
{
    if (_fs4hits.size() < maxHits) {
        _fs4hits.push_back(hit);
        if (_fs4hits.size() == maxHits) {
            std::make_heap(_fs4hits.begin(), _fs4hits.end());
        }
    } else {
        if (hit.cmp(_fs4hits.front()) < 0) {
            std::pop_heap(_fs4hits.begin(), _fs4hits.end());
            _fs4hits.push_back(hit);
            std::push_heap(_fs4hits.begin(), _fs4hits.end());
        }
    }
    return *this;
}

HitList & HitList::addHit(const VdsHit & hit, uint32_t maxHits)
{
    if (_vdshits.size() < maxHits) {
        _vdshits.push_back(hit);
        if (_vdshits.size() == maxHits) {
            std::make_heap(_vdshits.begin(), _vdshits.end());
        }
    } else {
        if (hit.cmp(_vdshits.front()) < 0) {
            std::pop_heap(_vdshits.begin(), _vdshits.end());
            _vdshits.push_back(hit);
            std::push_heap(_vdshits.begin(), _vdshits.end());
        }
    }
    return *this;
}

void
HitList::onMerge(const HitList & b)
{
    _fs4hits.insert(_fs4hits.end(), b._fs4hits.begin(), b._fs4hits.end());
    _vdshits.insert(_vdshits.end(), b._vdshits.begin(), b._vdshits.end());
}

void
HitList::sort()
{
    std::sort(_fs4hits.begin(), _fs4hits.end());
    std::sort(_vdshits.begin(), _vdshits.end());
}

void
HitList::postMerge(uint32_t maxHits)
{
    sort();
    if (_fs4hits.size() > maxHits) {
        _fs4hits.resize(maxHits);
    }
    if (_vdshits.size() > maxHits) {
        _vdshits.resize(maxHits);
    }
}

Serializer &
HitList::onSerialize(Serializer & os) const
{
    os << (uint32_t)(_fs4hits.size() + _vdshits.size());
    for (uint32_t i(0); i < _fs4hits.size(); i++) {
        HitCP hit(const_cast<FS4Hit *>(&_fs4hits[i]));
        os << hit;
        hit.release();
    }
    for (uint32_t i(0); i < _vdshits.size(); i++) {
        HitCP hit(const_cast<VdsHit *>(&_vdshits[i]));
        os << hit;
        hit.release();
    }
    return os;
}

Deserializer &
HitList::onDeserialize(Deserializer & is)
{
    uint32_t count(0);

    is >> count;
    for (uint32_t i(0); i < count; i++) {
        HitCP hit;
        is >> hit;
        if (hit->inherits(FS4Hit::classId)) {
            _fs4hits.push_back(static_cast<const FS4Hit &>(*hit));
        } else {
            _vdshits.push_back(static_cast<const VdsHit &>(*hit));
        }
    }
    return is;
}

void
HitList::clear()
{
    _fs4hits.clear();
    _vdshits.clear();
}

void
HitList::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    visit(visitor, "fs4hits", _fs4hits);
    visit(visitor, "vdshits", _vdshits);
}

void
HitList::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    for (uint32_t i(0); i < _fs4hits.size(); ++i) {
        _fs4hits[i].select(predicate, operation);
    }
    for (uint32_t i(0); i < _vdshits.size(); ++i) {
        _vdshits[i].select(predicate, operation);
    }
}

void
HitList::set(const ResultNode & rhs)
{
    (void) rhs;
    throw std::runtime_error("HitList::set(const ResultNode & rhs) not implemented.");
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_hitlist() {}
