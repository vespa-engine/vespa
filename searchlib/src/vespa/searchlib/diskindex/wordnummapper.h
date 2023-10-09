// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/string.h>
#include <limits>

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
    uint64_t _oldDictSize;
public:

    WordNumMapping();

    const uint64_t *getOld2NewWordNums() const {
        return (_old2newwords.empty())
            ? nullptr
            : &_old2newwords[0];
    }

    uint64_t getOldDictSize() const { return _oldDictSize; }
    void readMappingFile(const vespalib::string &name, const TuneFileSeqRead &tuneFileRead);
    void noMappingFile();
    void clear();
    void setup(uint32_t numWordIds);
};


class WordNumMapper
{
    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t noWordNum() { return 0u; }

    const uint64_t *_old2newwords;
    uint64_t _oldDictSize;

public:
    WordNumMapper()
        : _old2newwords(nullptr),
          _oldDictSize(0)
    {}

    void setup(const WordNumMapping &mapping) {
        _old2newwords = mapping.getOld2NewWordNums();
        _oldDictSize = mapping.getOldDictSize();
    }

    uint64_t map(uint32_t wordNum) const {
        return (_old2newwords != nullptr)
            ? _old2newwords[wordNum]
            : wordNum;
    }
};

}
