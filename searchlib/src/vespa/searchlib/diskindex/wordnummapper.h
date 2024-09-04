// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/util/array.h>
#include <limits>
#include <span>
#include <string>

namespace search::diskindex {

class WordNumMapper;

class WordNumMapping
{
    using Array = vespalib::Array<uint64_t>;

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t noWordNum() { return 0u; }

    Array _old2newwords;
public:

    WordNumMapping();

    std::span<const uint64_t> getOld2NewWordNums() const {
        return (_old2newwords.empty())
            ? std::span<const uint64_t>()
            : std::span<const uint64_t>(_old2newwords.data(), _old2newwords.size());
    }

    void readMappingFile(const std::string &name, const TuneFileSeqRead &tuneFileRead);
    void noMappingFile();
    void clear();
};


class WordNumMapper
{
    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t noWordNum() { return 0u; }

    std::span<const uint64_t>_old2newwords;

public:
    WordNumMapper()
        : _old2newwords()
    {}

    void setup(const WordNumMapping &mapping) {
        _old2newwords = mapping.getOld2NewWordNums();
    }

    uint64_t map(uint32_t wordNum) const {
        return (_old2newwords.data() != nullptr)
            ? _old2newwords[wordNum]
            : wordNum;
    }
};

}
