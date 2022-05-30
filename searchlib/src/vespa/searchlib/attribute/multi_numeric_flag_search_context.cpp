// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_numeric_flag_search_context.h"
#include "attributeiterators.hpp"
#include "attributevector.h"
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

using queryeval::SearchIterator;

template <typename T, typename M>
MultiNumericFlagSearchContext<T, M>::MultiNumericFlagSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view, AtomicBitVectorsRef bit_vectors)
    : MultiNumericSearchContext<T, M>(std::move(qTerm), toBeSearched, mv_mapping_read_view),
      _bit_vectors(bit_vectors),
      _zeroHits(false)
{
}

template <typename T, typename M>
std::unique_ptr<SearchIterator>
MultiNumericFlagSearchContext<T, M>::createIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (this->valid()) {
        if (this->_low == this->_high) {
            const AttributeVector & attr = this->attribute();
            const BitVector * bv(get_bit_vector(this->_low));
            if (bv != nullptr) {
                return BitVectorIterator::create(bv, attr.getCommittedDocIdLimit(), *matchData, strict);
            } else {
                return std::make_unique<queryeval::EmptySearch>();
            }
        } else {
            SearchIterator::UP flagIterator(
              strict
                 ? new FlagAttributeIteratorStrict<MultiNumericFlagSearchContext>(*this, matchData)
                 : new FlagAttributeIteratorT<MultiNumericFlagSearchContext>(*this, matchData));
            return flagIterator;
        }
    } else {
        return std::make_unique<queryeval::EmptySearch>();
    }
}

template class MultiNumericFlagSearchContext<int8_t, int8_t>;

}
