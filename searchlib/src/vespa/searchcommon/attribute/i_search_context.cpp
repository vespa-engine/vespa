// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_search_context.h"

namespace search::attribute {

void
ISearchContext::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) const
{
    int32_t weight(0);
    for (int32_t id = find(docid, 0, weight); id >= 0; id = find(docid, id+1, weight)) {
        element_ids.push_back(id);
    }
}

void
ISearchContext::and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) const
{
    size_t to_keep(0);
    int32_t id(-1);
    int32_t weight(0);
    for (int32_t candidate : element_ids) {
        if (candidate > id) {
            id = find(docid, candidate, weight);
            if (id < 0) break;
        }
        if (id == candidate) {
            element_ids[to_keep++] = candidate;
        }
    }
    element_ids.resize(to_keep);
}

}
