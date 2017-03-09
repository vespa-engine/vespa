// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termwise_blueprint_helper.h"
#include "termwise_search.h"

namespace search {
namespace queryeval {

TermwiseBlueprintHelper::TermwiseBlueprintHelper(const IntermediateBlueprint &self,
                                                 const MultiSearch::Children &subSearches,
                                                 UnpackInfo &unpackInfo)
    : children(),
      termwise(),
      first_termwise(subSearches.size()),
      termwise_unpack()
{
    children.reserve(subSearches.size());
    termwise.reserve(subSearches.size());
    for (size_t i = 0; i < subSearches.size(); ++i) {
        bool need_unpack = unpackInfo.needUnpack(i);
        bool allow_termwise = self.getChild(i).getState().allow_termwise_eval();
        if (need_unpack || !allow_termwise) {
            if (need_unpack) {
                size_t index = (i < first_termwise) ? children.size() : (children.size() + 1);
                termwise_unpack.add(index);
            }
            children.push_back(subSearches[i]);
        } else {
            first_termwise = std::min(i, first_termwise);
            termwise.push_back(subSearches[i]);
        }
    }
}

TermwiseBlueprintHelper::~TermwiseBlueprintHelper() { }

void
TermwiseBlueprintHelper::insert_termwise(SearchIterator::UP search, bool strict)
{
    auto termwise_search = make_termwise(std::move(search), strict);
    children.insert(children.begin() + first_termwise, termwise_search.release());
}

} // namespace queryeval
} // namespace search
