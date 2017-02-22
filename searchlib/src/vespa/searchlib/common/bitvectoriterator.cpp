// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectoriterator.h"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/objects/visit.h>

namespace search {

using fef::TermFieldMatchDataArray;
using fef::TermFieldMatchData;

BitVectorIterator::BitVectorIterator(const BitVector & bv, uint32_t docIdLimit, TermFieldMatchData & matchData) :
    _docIdLimit(std::min(docIdLimit, bv.size())),
    _bv(bv),
    _tfmd(matchData)
{
    assert(docIdLimit <= bv.size());
    _tfmd.reset(0);
}

void
BitVectorIterator::initRange(uint32_t begin, uint32_t end)
{
    SearchIterator::initRange(begin, end);
    if (begin >= _docIdLimit) {
        setAtEnd();
    } else {
        uint32_t docId = _bv.getFirstTrueBit(begin);
        if (docId >= _docIdLimit) {
            setAtEnd();
        } else {
            setDocId(docId);
        }
    }
}

void
BitVectorIterator::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else if (_bv.testBit(docId)) {
        setDocId(docId);
    }
}

void
BitVectorIterator::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    SearchIterator::visitMembers(visitor);
    visit(visitor, "docIdLimit", _docIdLimit);
    visit(visitor, "termfieldmatchdata.fieldId", _tfmd.getFieldId());
    visit(visitor, "termfieldmatchdata.docid", _tfmd.getDocId());
}

void
BitVectorIterator::doUnpack(uint32_t docId)
{
    _tfmd.resetOnlyDocId(docId);
}

class BitVectorIteratorStrict : public BitVectorIterator
{
public:
    BitVectorIteratorStrict(const BitVector & bv, uint32_t docIdLimit, TermFieldMatchData & matchData);
private:
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
};

BitVectorIteratorStrict::BitVectorIteratorStrict(const BitVector & bv, uint32_t docIdLimit, TermFieldMatchData & matchData) :
    BitVectorIterator(bv, docIdLimit, matchData)
{
}

void
BitVectorIteratorStrict::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
        return;
    }
    
    docId = _bv.getNextTrueBit(docId);
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else {
        setDocId(docId);
    }
}

queryeval::SearchIterator::UP BitVectorIterator::create(const BitVector *const bv, const TermFieldMatchDataArray &matchData, bool strict)
{
    assert(matchData.size() == 1);
    return create(bv, bv->size(), *matchData[0], strict);
}
queryeval::SearchIterator::UP BitVectorIterator::create(const BitVector *const bv, uint32_t docIdLimit, TermFieldMatchData &matchData, bool strict)
{
    if (bv == NULL) {
        return UP(new queryeval::EmptySearch());
    } else if (strict) {
        return UP(new BitVectorIteratorStrict(*bv, docIdLimit, matchData));
    } else {
        return UP(new BitVectorIterator(*bv, docIdLimit, matchData));
    }
}

BitVector::UP BitVectorIterator::get_hits(uint32_t begin_id) {
    BitVector::UP result = BitVector::create(_bv, begin_id, getEndId());
    if (begin_id < getDocId()) {
        result->clearInterval(begin_id, getDocId());
    }
    return result;
}

void BitVectorIterator::or_hits_into(BitVector &result, uint32_t begin_id) {
    (void) begin_id;
    result.orWith(_bv);
}

void BitVectorIterator::and_hits_into(BitVector &result, uint32_t begin_id) {
    (void) begin_id;
    result.andWith(_bv);
}


} // namespace search
