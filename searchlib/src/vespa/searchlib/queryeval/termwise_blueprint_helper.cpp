// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termwise_blueprint_helper.h"
#include "termwise_search.h"

namespace search::queryeval {

TermwiseBlueprintHelper::TermwiseBlueprintHelper(const IntermediateBlueprint &self,
                                                 MultiSearch::Children subSearches,
                                                 UnpackInfo &unpackInfo)
    : termwise_ch(),
      other_ch(),
      first_termwise(subSearches.size()),
      termwise_unpack()
{
    other_ch.reserve(subSearches.size());
    termwise_ch.reserve(subSearches.size());
    for (size_t i = 0; i < subSearches.size(); ++i) {
        bool need_unpack = unpackInfo.needUnpack(i);
        bool allow_termwise = self.getChild(i).getState().allow_termwise_eval();
        if (need_unpack || !allow_termwise) {
            if (need_unpack) {
                size_t index = (i < first_termwise) ? other_ch.size() : (other_ch.size() + 1);
                termwise_unpack.add(index);
            }
            other_ch.push_back(std::move(subSearches[i]));
        } else {
            first_termwise = std::min(i, first_termwise);
            termwise_ch.push_back(std::move(subSearches[i]));
        }
    }
}

TermwiseBlueprintHelper::~TermwiseBlueprintHelper() = default;

void
TermwiseBlueprintHelper::insert_termwise(SearchIterator::UP search, bool strict)
{
    auto termwise_search = make_termwise(std::move(search), strict);
    other_ch.insert(other_ch.begin() + first_termwise, std::move(termwise_search));
}

}
