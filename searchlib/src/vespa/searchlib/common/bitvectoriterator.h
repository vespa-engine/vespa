// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search {

namespace fef { class TermFieldMatchDataArray; }

class BitVectorIterator : public queryeval::SearchIterator
{
protected:
    BitVectorIterator(const BitVector & bv, uint32_t docIdLimit, fef::TermFieldMatchData &matchData);
    void initRange(uint32_t begin, uint32_t end) override;

    uint32_t          _docIdLimit;
    const BitVector & _bv;
private:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void doUnpack(uint32_t docId) final {
        _tfmd.resetOnlyDocId(docId);
    }
    BitVectorMeta asBitVector() const noexcept override { return {&_bv, _docIdLimit, isInverted()}; }
    fef::TermFieldMatchData  &_tfmd;
public:
    virtual bool isInverted() const = 0;

    Trinary is_strict() const override { return Trinary::False; }
    uint32_t getDocIdLimit() const noexcept { return _docIdLimit; }
    static UP create(const BitVector *const other, fef::TermFieldMatchData &matchData, bool strict, bool inverted = false);
    static UP create(const BitVector *const other, uint32_t docIdLimit,
                     fef::TermFieldMatchData &matchData, bool strict, bool inverted = false);
};

} // namespace search
