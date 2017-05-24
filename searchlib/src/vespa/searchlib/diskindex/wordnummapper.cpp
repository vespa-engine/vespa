// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".diskindex.wordnummapper");
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include "wordnummapper.h"

namespace search
{

namespace diskindex
{

WordNumMapping::WordNumMapping()
    : _old2newwords(),
      _oldDictSize(0u)
{
}


void
WordNumMapping::readMappingFile(const vespalib::string &name,
                                const TuneFileSeqRead &tuneFileRead)
{
    // Open word mapping file
    Fast_BufferedFile old2newwordfile(new FastOS_File);
    if (tuneFileRead.getWantDirectIO())
        old2newwordfile.EnableDirectIO();
    // XXX no checking for success
    old2newwordfile.ReadOpen(name.c_str());
    int64_t tempfilesize = old2newwordfile.GetSize();
    uint64_t tempfileentries = static_cast<uint64_t>(tempfilesize /
            sizeof(uint64_t));
    Array &map = _old2newwords;
    map.resize(tempfileentries + 2);
    _oldDictSize = tempfileentries;

    old2newwordfile.Read(&map[1],
                         static_cast<size_t>(tempfilesize));
    old2newwordfile.Close();
    map[0] = noWordNum();
    map[tempfileentries + 1] = noWordNumHigh();
}


void
WordNumMapping::noMappingFile()
{
    Array &map = _old2newwords;
    map.resize(2);
    map[0] = noWordNum();
    map[1] = noWordNumHigh();
    _oldDictSize = 0;
}


void
WordNumMapping::clear()
{
    Array &map = _old2newwords;
    map.clear();
    _oldDictSize = 0;
}


void
WordNumMapping::setup(uint32_t numWordIds)
{
    _oldDictSize = numWordIds;
}


void
WordNumMapper::sanityCheck(bool allowHoles)
{
    uint64_t dictSize = getMaxWordNum();
    uint64_t mappedWordNum = map(0u);
    assert(mappedWordNum == 0u);
    for (uint64_t wordNum = 1; wordNum <= dictSize; ++wordNum) {
        uint64_t prevMappedWordNum = mappedWordNum;
        mappedWordNum = map(wordNum);
        if (mappedWordNum == 0u && allowHoles)
            continue;	// In case some words are being removed
        assert(mappedWordNum > prevMappedWordNum);
        (void) prevMappedWordNum;
    }
}


uint64_t
WordNumMapping::getMaxMappedWordNum() const
{
    WordNumMapper mapper(*this);
    return mapper.getMaxMappedWordNum();
}


void
WordNumMapping::sanityCheck(bool allowHoles)
{
    WordNumMapper mapper(*this);
    mapper.sanityCheck(allowHoles);
}


} // namespace diskindex

} // namespace search
