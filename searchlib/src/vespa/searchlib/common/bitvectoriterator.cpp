// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectoriterator.h"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/objects/visit.h>
#include <cassert>

namespace search {

using fef::TermFieldMatchDataArray;
using fef::TermFieldMatchData;
using vespalib::Trinary;

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

template<bool inverse>
class BitVectorIteratorT : public BitVectorIterator {
public:
    BitVectorIteratorT(const BitVector &other, uint32_t docIdLimit, fef::TermFieldMatchData &matchData);

    void doSeek(uint32_t docId) override;
    BitVector::UP get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    bool isInverted() const override { return inverse; }
private:
    bool isSet(uint32_t docId) const noexcept { return inverse == ! _bv.testBit(docId); }
};

template<bool inverse>
BitVectorIteratorT<inverse>::BitVectorIteratorT(const BitVector & bv, uint32_t docIdLimit, TermFieldMatchData & matchData) :
    BitVectorIterator(bv, docIdLimit, matchData)
{
}

template<bool inverse>
void
BitVectorIteratorT<inverse>::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else if (isSet(docId)) {
        setDocId(docId);
    }
}

template<bool inverse>
class BitVectorIteratorStrictT : public BitVectorIteratorT<inverse>
{
public:
    BitVectorIteratorStrictT(const BitVector & bv, uint32_t docIdLimit, TermFieldMatchData & matchData);
private:
    void initRange(uint32_t begin, uint32_t end) override;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
    uint32_t getNextBit(uint32_t docId) const noexcept {
        return inverse ? this->_bv.getNextFalseBit(docId) : this->_bv.getNextTrueBit(docId);
    }
    uint32_t getFirstBit(uint32_t docId) const noexcept {
        return inverse ? this->_bv.getFirstFalseBit(docId) : this->_bv.getFirstTrueBit(docId);
    }
};

template<bool inverse>
BitVectorIteratorStrictT<inverse>::BitVectorIteratorStrictT(const BitVector & bv, uint32_t docIdLimit, TermFieldMatchData & matchData) :
    BitVectorIteratorT<inverse>(bv, docIdLimit, matchData)
{
}

template<bool inverse>
void
BitVectorIteratorStrictT<inverse>::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= this->_docIdLimit, false)) {
        this->setAtEnd();
        return;
    }
    
    docId = getNextBit(docId);
    if (__builtin_expect(docId >= this->_docIdLimit, false)) {
        this->setAtEnd();
    } else {
        this->setDocId(docId);
    }
}

template<bool inverse>
void
BitVectorIteratorStrictT<inverse>::initRange(uint32_t begin, uint32_t end)
{
    BitVectorIterator::initRange(begin, end);
    if (!this->isAtEnd()) {
        uint32_t docId = getFirstBit(begin);
        if (docId >= this->_docIdLimit) {
            this->setAtEnd();
        } else {
            this->setDocId(docId);
        }
    }
}

template <typename Parent, bool full_reset>
class BitVectorIteratorTT : public Parent {
public:
    BitVectorIteratorTT(const BitVector& bv, uint32_t docid_limit, TermFieldMatchData& tfmd);
    ~BitVectorIteratorTT() override;
    void doUnpack(uint32_t docId) override;
};

template<typename Parent, bool full_reset>
BitVectorIteratorTT<Parent, full_reset>::BitVectorIteratorTT(const BitVector& bv, uint32_t docid_limit, TermFieldMatchData& tfmd)
    : Parent(bv, docid_limit, tfmd)
{
}

template<typename Parent, bool full_reset>
BitVectorIteratorTT<Parent, full_reset>::~BitVectorIteratorTT() = default;

template <typename Parent, bool full_reset>
void
BitVectorIteratorTT<Parent, full_reset>::doUnpack(uint32_t docId)
{
    if constexpr (full_reset) {
        this->_tfmd.reset(docId);
    } else {
        this->_tfmd.resetOnlyDocId(docId);
    }
}

namespace
{

struct StrictIteratorType {
    template <bool inverted, bool full_reset> using type = BitVectorIteratorTT<BitVectorIteratorStrictT<inverted>, full_reset>;
};

struct IteratorType {
    template <bool inverted, bool full_reset> using type = BitVectorIteratorTT<BitVectorIteratorT<inverted>, full_reset>;
};

template <typename IteratorType, bool full_reset>
std::unique_ptr<queryeval::SearchIterator>
create_helper_helper(const BitVector& bv, uint32_t docid_limit, TermFieldMatchData& tfmd, bool inverted)
{
    if (inverted) {
        return std::make_unique<typename IteratorType::template type<true, full_reset>>(bv, docid_limit, tfmd);
    } else {
        return std::make_unique<typename IteratorType::template type<false, full_reset>>(bv, docid_limit, tfmd);
    }
}

template <typename IteratorType>
std::unique_ptr<queryeval::SearchIterator>
create_helper(const BitVector& bv, uint32_t docid_limit, TermFieldMatchData& tfmd, bool inverted, bool full_reset)
{
    if (full_reset) {
        return create_helper_helper<IteratorType, true>(bv, docid_limit, tfmd, inverted);
    } else {
        return create_helper_helper<IteratorType, false>(bv, docid_limit, tfmd, inverted);

    }
}

}

queryeval::SearchIterator::UP
BitVectorIterator::create(const BitVector *const bv, TermFieldMatchData &matchData, bool strict, bool inverted)
{
    return create(bv, bv->size(), matchData, strict, inverted, false);
}

queryeval::SearchIterator::UP
BitVectorIterator::create(const BitVector *const bv, uint32_t docIdLimit,
                          TermFieldMatchData &matchData, bool strict, bool inverted)
{
    return create(bv, docIdLimit, matchData, strict, inverted, false);
}

queryeval::SearchIterator::UP
BitVectorIterator::create(const BitVector *const bv, uint32_t docid_limit,
                          TermFieldMatchData& tfmd, bool strict, bool inverted, bool full_reset)
{
    if (bv == nullptr) {
        return std::make_unique<queryeval::EmptySearch>();
    } else if (strict) {
        return create_helper<StrictIteratorType>(*bv, docid_limit, tfmd, inverted, full_reset);
    } else {
        return create_helper<IteratorType>(*bv, docid_limit, tfmd, inverted, full_reset);
    }
}

template<bool inverse>
BitVector::UP
BitVectorIteratorT<inverse>::get_hits(uint32_t begin_id) {
    BitVector::UP result = BitVector::create(_bv, begin_id, getEndId());
    if (inverse) {
        result->notSelf();
    }
    if (begin_id < getDocId()) {
        result->clearInterval(begin_id, getDocId());
    }
    return result;
}

template<bool inverse>
void
BitVectorIteratorT<inverse>::or_hits_into(BitVector &result, uint32_t) {
    if (inverse) {
        result.notSelf();
        result.andWith(_bv);
        result.notSelf();
    } else {
        result.orWith(_bv);
    }
}

template<bool inverse>
void
BitVectorIteratorT<inverse>::and_hits_into(BitVector &result, uint32_t) {
    if (inverse) {
        result.andNotWith(_bv);
    } else {
        result.andWith(_bv);
    }
}

} // namespace search
