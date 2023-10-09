// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cassert>
#include <cstdint>
#include <cstring>
#include <vector>

namespace search::test
{

/*
 * Class used by hnsw graph/index unit tests to load hnsw index from a
 * vector.
 */
class VectorBufferReader {
private:
    const std::vector<char>& _data;
    size_t _pos;

public:
    VectorBufferReader(const std::vector<char>& data) : _data(data), _pos(0) {}
    uint32_t readHostOrder() {
        uint32_t result = 0;
        assert(_pos + sizeof(uint32_t) <= _data.size());
        std::memcpy(&result, _data.data() + _pos, sizeof(uint32_t));
        _pos += sizeof(uint32_t);
        return result;
    }
};

}
