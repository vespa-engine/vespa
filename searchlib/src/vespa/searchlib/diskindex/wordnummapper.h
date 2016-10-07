// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/array.h>
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search
{

namespace diskindex
{

class WordNumMapper;

class WordNumMapping
{
    typedef vespalib::Array<uint64_t> Array;

    static uint64_t
    noWordNumHigh(void)
    {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t
    noWordNum(void)
    {
        return 0u;
    }

    Array _old2newwords;
    uint64_t _oldDictSize;
public:

    WordNumMapping(void);

    const uint64_t *
    getOld2NewWordNums(void) const
    {
        return (_old2newwords.empty())
            ? NULL
            : &_old2newwords[0];
    }

    uint64_t
    getOldDictSize(void) const
    {
        return _oldDictSize;
    }

    void
    readMappingFile(const vespalib::string &name,
                    const TuneFileSeqRead &tuneFileRead);

    void
    noMappingFile(void);

    void
    clear(void);

    void
    setup(uint32_t numWordIds);

    uint64_t
    getMaxMappedWordNum(void) const;

    void
    sanityCheck(bool allowHoles);
};


class WordNumMapper
{
    static uint64_t
    noWordNumHigh(void)
    {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t
    noWordNum(void)
    {
        return 0u;
    }

    const uint64_t *_old2newwords;
    uint64_t _oldDictSize;

public:
    WordNumMapper(void)
        : _old2newwords(NULL),
          _oldDictSize(0)
    {
    }

    WordNumMapper(const WordNumMapping &mapping)
        : _old2newwords(NULL),
          _oldDictSize(0)
    {
        setup(mapping);
    }

    void
    setup(const WordNumMapping &mapping)
    {
        _old2newwords = mapping.getOld2NewWordNums();
        _oldDictSize = mapping.getOldDictSize();
    }

    uint64_t
    map(uint32_t wordNum) const
    {
        return (_old2newwords != NULL)
            ? _old2newwords[wordNum]
            : wordNum;
    }

    uint64_t
    getMaxWordNum(void) const
    {
        return _oldDictSize;
    }

    uint64_t
    getMaxMappedWordNum(void) const
    {
        return map(_oldDictSize);
    }

    void
    sanityCheck(bool allowHoles);
};

} // namespace diskindex

} // namespace search

