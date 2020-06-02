// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <assert.h>

namespace search::queryeval {

class MultiSearch;

/**
 * This class owns a set of SearchIterator instances, for
 * holding them or passing ownership around.
 **/
class ChildrenIterators {
    private:
        std::vector<SearchIterator::UP> _data;
        friend class MultiSearch;
    public:
        ChildrenIterators() {}
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

        size_t size() const { return _data.size(); }
        bool empty() const { return _data.size() == 0; }
        void reserve(size_t sz) { _data.reserve(sz); }
        void clear() { _data.clear(); }
        void push_back(SearchIterator::UP search) {
            _data.push_back(std::move(search));
        }
        template <typename Arg>
        void emplace_back(Arg && search) {
            _data.emplace_back(std::forward<Arg>(search));
        }
        const SearchIterator::UP & operator[] (size_t idx) const {
            return _data[idx];
        }
        SearchIterator::UP & operator[] (size_t idx) {
            return _data[idx];
        }
        void insert(size_t index, SearchIterator::UP search) {
            assert(index <= _data.size());
            _data.insert(_data.begin()+index, std::move(search));
        }
        SearchIterator::UP remove(size_t index) {
            assert(index < _data.size());
            SearchIterator::UP search = std::move(_data[index]);
            _data.erase(_data.begin() + index);
            return search;
        }
        auto begin() const { return _data.begin(); }
        auto end() const { return _data.end(); }
        auto begin() { return _data.begin(); }
        auto end() { return _data.end(); }
};

} // namespace
