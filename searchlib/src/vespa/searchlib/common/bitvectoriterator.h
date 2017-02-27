// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"
#include <vespa/searchlib/queryeval/searchiterator.h>


namespace search {

namespace fef { class TermFieldMatchDataArray; }
namespace fef { class TermFieldMatchData; }

class BitVectorIterator : public queryeval::SearchIterator
{
protected:
    BitVectorIterator(const BitVector & other, uint32_t docIdLimit, fef::TermFieldMatchData &matchData);

    uint32_t          _docIdLimit;
    const BitVector & _bv;
private:
    void initRange(uint32_t begin, uint32_t end) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    BitVector::UP get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    bool isBitVector() const override { return true; }
    fef::TermFieldMatchData  &_tfmd;
public:
    const void * getBitValues() const { return _bv.getStart(); }

    Trinary is_strict() const override { return Trinary::False; }
    virtual bool isStrict() const { return (is_strict() == Trinary::True); }
    uint32_t getDocIdLimit() const { return _docIdLimit; }
    static UP create(const BitVector *const other, const fef::TermFieldMatchDataArray &matchData, bool strict);
    static UP create(const BitVector *const other, uint32_t docIdLimit, fef::TermFieldMatchData &matchData, bool strict);
};


} // namespace search


