// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchiterator.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

SearchIterator::SearchIterator() :
    _docid(0),
    _endid(0)
{ }

void
SearchIterator::initRange(uint32_t beginid, uint32_t endid)
{
    _docid = beginid - 1;
    _endid = endid;
}

BitVector::UP
SearchIterator::get_hits(uint32_t begin_id)
{
    BitVector::UP result(BitVector::create(begin_id, getEndId()));
    uint32_t docid = std::max(begin_id, getDocId());
    while (!isAtEnd(docid)) {
        if (seek(docid)) {
            result->setBit(docid);
        }
        docid = std::max(docid + 1, getDocId());
    }
    result->invalidateCachedCount();
    return result;
}

SearchIterator::UP
SearchIterator::andWith(UP filter, uint32_t estimate)
{
    (void) estimate;
    return filter;
}

void
SearchIterator::or_hits_into(BitVector &result, uint32_t begin_id)
{
    uint32_t docid = std::max(begin_id, getDocId());
    while (!isAtEnd(docid)) {
        docid = result.getNextFalseBit(docid);
        if (!isAtEnd(docid) && seek(docid)) {
            result.setBit(docid);
        }
        docid = std::max(docid + 1, getDocId());
    }
    result.invalidateCachedCount();
}

void
SearchIterator::and_hits_into_non_strict(BitVector &result, uint32_t begin_id)
{
    result.foreach_truebit([&](uint32_t key) { if ( ! seek(key)) { result.clearBit(key); }}, begin_id);
    result.invalidateCachedCount();
}

void
SearchIterator::and_hits_into_strict(BitVector &result, uint32_t begin_id)
{
    seek(begin_id);
    uint32_t docidA = getDocId();
    uint32_t docidB = result.getNextTrueBit(begin_id);
    while (!isAtEnd(docidB) && !isAtEnd(docidA)) {
        if (docidA < docidB) {
            if (seek(docidB)) {
                docidA = docidB;
            } else {
                docidA = getDocId();
            }
        } else if (docidA > docidB) {
            result.clearInterval(docidB, docidA);
            docidB = result.getNextTrueBit(docidA);
        } else {
            docidB = result.getNextTrueBit(docidB+1);
        }
    }
    result.clearInterval(docidB, result.size());
}

void
SearchIterator::and_hits_into(BitVector &result, uint32_t begin_id)
{
    if (is_strict() == vespalib::Trinary::True) {
        and_hits_into_strict(result, begin_id);
    } else {
        and_hits_into_non_strict(result, begin_id);
    }
}

vespalib::string
SearchIterator::asString() const
{
    vespalib::ObjectDumper dumper;
    visit(dumper, "", this);
    return dumper.toString();
}

vespalib::string
SearchIterator::getClassName() const
{
    return vespalib::getClassName(*this);
}

void
SearchIterator::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "docid", _docid);
    visit(visitor, "endid", _endid);
}

const attribute::ISearchContext *
SearchIterator::getAttributeSearchContext() const
{
    return nullptr;
}

} // namespace queryeval
} // namespace search

//-----------------------------------------------------------------------------

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::SearchIterator *obj)
{
    if (obj != 0) {
        self.openStruct(name, obj->getClassName());
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::SearchIterator &obj)
{
    visit(self, name, &obj);
}
