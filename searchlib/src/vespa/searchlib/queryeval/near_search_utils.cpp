// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_search_utils.h"
#include <algorithm>

namespace search::queryeval::near_search_utils {

void
ElementIdMatchResult::maybe_sort_element_ids()
{
    if (_need_sort) {
        std::sort(_element_ids.begin(), _element_ids.end());
        _element_ids.resize(std::unique(_element_ids.begin(), _element_ids.end()) - _element_ids.begin());
    }
}

}
