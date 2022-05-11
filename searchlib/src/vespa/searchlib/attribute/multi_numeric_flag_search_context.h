// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_numeric_search_context.h"
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>

namespace search {
class BitVector;
template <class SC> class FlagAttributeIteratorT;
template <class SC> class FlagAttributeIteratorStrict;
}

namespace search::attribute {

/*
 * MultiNumericFlagSearchContext handles the creation of search iterators for
 * a query term on a multi value numeric flag attribute vector.
 */
template <typename T, typename M>
class MultiNumericFlagSearchContext : public MultiNumericSearchContext<T, M>
{
public:
    using AtomicBitVectorsRef = vespalib::ConstArrayRef<vespalib::datastore::AtomicValueWrapper<BitVector *>>;

    MultiNumericFlagSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view,
                                  AtomicBitVectorsRef bit_vectors);

    std::unique_ptr<queryeval::SearchIterator>
    createIterator(fef::TermFieldMatchData * matchData, bool strict) override;
private:
    AtomicBitVectorsRef _bit_vectors;
    bool _zeroHits;
    const BitVector* get_bit_vector(T value) const {
        static_assert(std::is_same_v<T, int8_t>, "Flag attribute search context is only supported for int8_t data type");
        return _bit_vectors[value + 128].load_acquire();
    }

    template <class SC> friend class ::search::FlagAttributeIteratorT;
    template <class SC> friend class ::search::FlagAttributeIteratorStrict;
};

}
