// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"

namespace search::queryeval {

/**
 * Creates a termwise wrapper for the given search. The wrapper will
 * perform termwise evaluation of the underlying search when the
 * initRange function is called. All hits for the active range are
 * stored in a bitvector fragment in the wrapper. The wrapper will act
 * as a normal iterator to be used for parallel query evaluation. Note
 * that no match data will be available for the hits returned by the
 * wrapper. Termwise evaluation should only ever be used for parts of
 * the query not used for ranking.
 *
 * @return wrapper performing termwise evaluation of the original search
 * @param search the search we want to perform termwise evaluation of
 * @param strict whether the wrapper itself should be a strict iterator
 **/
SearchIterator::UP make_termwise(SearchIterator::UP search, bool strict);

}
