// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <cassert>

namespace search {
class RandomGenerator
{
private:
    vespalib::Rand48 _rnd;

public:
    RandomGenerator() : _rnd() {}

    RandomGenerator(long seed) : _rnd() {
        _rnd.srand48(seed);
    }

    void srand(long seed) {
        _rnd.srand48(seed);
    }

    uint32_t rand(uint32_t min, uint32_t max) {
        assert(min <= max);
        uint32_t divider = max - min + 1;
        return (divider == 0 ? _rnd.lrand48() : min + _rnd.lrand48() % divider);
    }

    vespalib::string getRandomString(uint32_t minLen, uint32_t maxLen) {
        uint32_t len = rand(minLen, maxLen);
        vespalib::string retval;
        for (uint32_t i = 0; i < len; ++i) {
            char c = static_cast<char>(rand('a', 'z'));
            retval.push_back(c);
        }
        return retval;
    }

    void fillRandomStrings(std::vector<vespalib::string> & vec, uint32_t numStrings,
                           uint32_t minLen, uint32_t maxLen) {
        vec.clear();
        vec.reserve(numStrings);
        for (uint32_t i = 0; i < numStrings; ++i) {
            vec.push_back(getRandomString(minLen, maxLen));
        }
    }

    template <typename T>
    void fillRandomIntegers(std::vector<T> & vec, uint32_t numValues) {
        vec.clear();
        vec.reserve(numValues);
        for (uint32_t i = 0; i < numValues; ++i) {
            vec.push_back(static_cast<T>(_rnd.lrand48()));
        }
    }
};

} // search

