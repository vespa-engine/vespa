// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    BitVector::UP tmp = get_hits(begin_id);
    const BitVector &rhs = *tmp;
    result.orWith(rhs);
}

void
SearchIterator::and_hits_into(BitVector &result, uint32_t begin_id)
{
    BitVector::UP tmp = get_hits(begin_id);
    const BitVector &rhs = *tmp;
    result.andWith(rhs);
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

namespace {

using Children = SearchIterator::Children;

void
andIterators(BitVector::UP &result, const Children &children, uint32_t begin_id, bool onlyBitVector) {
    for (SearchIterator *child : children) {
        if (child->isBitVector() == onlyBitVector) {
            if (!result) {
                result = child->get_hits(begin_id);
            } else {
                child->and_hits_into(*result, begin_id);
            }
        }
    }
}

template<typename Children>
void
orIterators(BitVector::UP &result, const Children &children, uint32_t begin_id, bool onlyBitVector) {
    for (auto & child : children) {
        if (child->isBitVector() == onlyBitVector) {
            if (!result) {
                result = child->get_hits(begin_id);
            } else {
                child->or_hits_into(*result, begin_id);
            }
        }
    }
}

}

void
SearchIterator::andChildren(BitVector::UP &result, const Children &children, uint32_t begin_id) {
    andIterators(result, children, begin_id, true);
    andIterators(result, children, begin_id, false);
}

void
SearchIterator::orChildren(BitVector::UP &result, const Children &children, uint32_t begin_id) {
    orIterators(result, children, begin_id, true);
    orIterators(result, children, begin_id, false);
}

void
SearchIterator::orChildren(BitVector::UP &result, const OwnedChildren &children, uint32_t begin_id) {
    orIterators(result, children, begin_id, true);
    orIterators(result, children, begin_id, false);
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
