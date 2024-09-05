// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <limits>
#include <memory>
#include <span>
#include <string>

class FastOS_FileInterface;

namespace search::diskindex {

class WordNumMapper;

class WordNumMapping
{
    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t noWordNum() { return 0u; }

    std::unique_ptr<FastOS_FileInterface> _file;
    std::span<const uint64_t> _mapping;
    static const uint64_t _no_mapping[2];
public:

    WordNumMapping();
    WordNumMapping(const WordNumMapping&) = delete;
    WordNumMapping(WordNumMapping&&) noexcept;
    ~WordNumMapping();

    std::span<const uint64_t> getOld2NewWordNums() const noexcept {
        return _mapping;
    }

    void readMappingFile(const std::string &name);
    void noMappingFile();
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
