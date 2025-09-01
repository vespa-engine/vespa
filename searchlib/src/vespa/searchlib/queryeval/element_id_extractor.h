// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vector>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

/*
 * Helper class to extract element ids from TermFieldMatchData as part of sameElement evaluation.
 * Used by search iterators for disk term, phrase and equiv.
 */
class ElementIdExtractor {
public:
    static void get_element_ids(const fef::TermFieldMatchData& tfmd, uint32_t docid,
                                std::vector<uint32_t>& element_ids);
    static void and_element_ids_into(const fef::TermFieldMatchData& tfmd, uint32_t docid,
                                     std::vector<uint32_t>& elementIds);
};

}
