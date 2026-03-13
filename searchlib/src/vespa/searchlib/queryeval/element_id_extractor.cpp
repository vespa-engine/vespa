// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "element_id_extractor.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::queryeval {

void
ElementIdExtractor::get_element_ids(const fef::TermFieldMatchData& tfmd, uint32_t docid,
                                    std::vector<uint32_t>& element_ids)
{
    if (tfmd.has_data(docid)) {
        int32_t prevId(-1);
        for (auto element: tfmd) {
            uint32_t id(element.getElementId());
            if (prevId != int32_t(id)) {
                element_ids.push_back(id);
                prevId = id;
            }
        }
    }
}

void
ElementIdExtractor::and_element_ids_into(const fef::TermFieldMatchData& tfmd, uint32_t docid,
                                         std::vector<uint32_t>& element_ids)
{
    if (tfmd.has_data(docid)) {
        size_t toKeep(0);
        int32_t id(-1);
        auto it = tfmd.begin();
        for (int32_t candidate : element_ids) {
            if (candidate > id) {
                while ((it != tfmd.end()) && (candidate > int32_t(it->getElementId()))) {
                    ++it;
                }
                if (it == tfmd.end()) break;
                id = it->getElementId();
            }
            if (id == candidate) {
                element_ids[toKeep++] = candidate;
            }
        }
        element_ids.resize(toKeep);
    } else {
        element_ids.clear();
    }
}

}
