// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchiterator.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/vespalib/objects/object2slime.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <algorithm>
#include <cassert>

namespace search::queryeval {

std::string
SearchIterator::make_id_ref_str() const
{
    if (_id == 0) {
        return "[]";
    }
    return vespalib::make_string("[%u]", _id);
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

std::string
SearchIterator::asString() const
{
    vespalib::ObjectDumper dumper;
    visit(dumper, "", this);
    return dumper.toString();
}

vespalib::slime::Cursor &
SearchIterator::asSlime(const vespalib::slime::Inserter & inserter) const
{
    vespalib::slime::Cursor & cursor = inserter.insertObject();
    vespalib::Object2Slime dumper(cursor);
    visit(dumper, "", this);
    return cursor;
}

std::string
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

void
SearchIterator::transform_children(std::function<SearchIterator::UP(SearchIterator::UP)>)
{
}

void
SearchIterator::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    (void) docid;
    assert(element_ids.empty());
}

void
SearchIterator::and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    if (element_ids.empty()) {
        return;
    }
    std::vector<uint32_t> temp_element_ids;
    std::vector<uint32_t> result;
    get_element_ids(docid, temp_element_ids);
    std::set_intersection(element_ids.begin(), element_ids.end(), temp_element_ids.begin(), temp_element_ids.end(),
                          std::back_inserter(result));
    std::swap(result, element_ids);
}

} // search::queryeval

//-----------------------------------------------------------------------------

void visit(vespalib::ObjectVisitor &self, std::string_view name,
           const search::queryeval::SearchIterator *obj)
{
    if (obj != nullptr) {
        self.openStruct(name, obj->getClassName());
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, const search::queryeval::SearchIterator &obj)
{
    visit(self, name, &obj);
}
