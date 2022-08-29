// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_index.h"
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search::memoryindex {

/**
 * Factory for creating search iterator over memory field index posting list.
 *
 * The template parameter specifies whether the wrapped posting list has interleaved features or not.
 *
 * @param itr           the posting list iterator to base the search iterator upon.
 * @param feature_store reference to store for features.
 * @param field_id      the id of the field searched.
 * @param match_data    the match data to unpack features into.
 */
template <bool interleaved_features>
queryeval::SearchIterator::UP
make_search_iterator(typename FieldIndex<interleaved_features>::PostingList::ConstIterator itr,
                     const FeatureStore& feature_store,
                     uint32_t field_id,
                     fef::TermFieldMatchDataArray match_data);

}

