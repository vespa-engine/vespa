// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>

namespace search::predicate {

/**
 * Builds a path from the root of a tree, to be able to describe a
 * given position in the tree.
 */
class TreeCrumbs {
    std::vector<char> _buffer;

public:
    void setChild(size_t number, char delimiter = ':') {
        _buffer.push_back(delimiter);
        char buf[10];
        int i = 0;
        while (number > 0) {
            buf[i++] = (number % 10) + '0';
            number /= 10;
        }
        if (i == 0) {
            _buffer.push_back('0');
        }
        while (i > 0) {
            _buffer.push_back(buf[--i]);
        }
    }
    void resize(size_t i) { _buffer.resize(i); }

    size_t size() const { return _buffer.size(); }
    std::string getCrumb() const {
        if (_buffer.empty()) {
            return std::string();
        }
        return std::string(&_buffer[0], _buffer.size());
    }
};

}
