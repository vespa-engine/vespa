// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::attribute { class ISearchContext; }
namespace search {

namespace fef { class TermFieldMatchDataArray; }

class BitVectorIterator : public queryeval::SearchIterator
{
protected:
    BitVectorIterator(const BitVector& bv, uint32_t docIdLimit, fef::TermFieldMatchData& matchData,
                      const attribute::ISearchContext* search_context);
    void initRange(uint32_t begin, uint32_t end) override;

    uint32_t          _docIdLimit;
    const BitVector & _bv;
    fef::TermFieldMatchData  &_tfmd;
    const attribute::ISearchContext* _search_context;
private:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    BitVectorMeta asBitVector() const noexcept override { return {&_bv, _docIdLimit, isInverted()}; }
public:
    virtual bool isInverted() const = 0;

    Trinary is_strict() const override { return Trinary::False; }
    uint32_t getDocIdLimit() const noexcept { return _docIdLimit; }
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;
    void and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) override;

    static UP create(const BitVector *const other, fef::TermFieldMatchData &matchData, bool strict, bool inverted = false);
    static UP create(const BitVector *const other, uint32_t docIdLimit,
                     fef::TermFieldMatchData &matchData, bool strict, bool inverted = false);
    static UP create(const BitVector *const other, uint32_t docIdLimit,
                     fef::TermFieldMatchData &matchData, const attribute::ISearchContext* search_context,
                     bool strict, bool inverted, bool full_reset);
};

} // namespace search
