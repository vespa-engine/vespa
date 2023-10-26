// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"

namespace search::queryeval {

/**
 * Convenience for constructing MultiSearch::Children
 * holding them or passing ownership around.
 **/
class ChildrenIterators {
    private:
        std::vector<SearchIterator::UP> _data;
    public:
        ChildrenIterators(std::vector<SearchIterator::UP> data)
          : _data(std::move(data)) {}
        ChildrenIterators(ChildrenIterators && other) = default;

        // convenience constructors for unit tests:
        template <typename... Args>
        ChildrenIterators(SearchIterator::UP a, Args&&... args) {
            _data.reserve(1 + sizeof...(Args));
            _data.push_back(std::move(a));
            (_data.emplace_back(std::forward<Args>(args)), ...);
        }
        template <typename... Args>
        ChildrenIterators(SearchIterator *a, Args&&... args) {
            _data.reserve(1 + sizeof...(Args));
            _data.emplace_back(a);
            (_data.emplace_back(std::forward<Args>(args)), ...);
        }
        operator std::vector<SearchIterator::UP> () && {
            return std::move(_data);
        }
};

} // namespace
