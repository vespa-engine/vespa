// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4_mem_writer.h"
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search::diskindex::test {

PageDict4MemWriter::PageDict4MemWriter(uint32_t chunkSize, uint64_t numWordIds, uint32_t ssPad, uint32_t spPad, uint32_t pPad)
    : _encoders(chunkSize, numWordIds),
      _buffers(_encoders.sse, _encoders.spe, _encoders.pe),
      _ssw(nullptr),
      _spw(nullptr),
      _pw(nullptr)
{
    _buffers.startPad(ssPad, spPad, pPad);
    allocWriters();
}

PageDict4MemWriter::~PageDict4MemWriter()
{
    delete _ssw;
    delete _spw;
    delete _pw;
}

void
PageDict4MemWriter::allocWriters()
{
    _ssw = new PageDict4SSWriter(_encoders.sse);
    _spw = new PageDict4SPWriter(*_ssw, _encoders.spe);
    _pw = new PageDict4PWriter(*_spw, _encoders.pe);
    _spw->setup();
    _pw->setup();
}

void
PageDict4MemWriter::flush()
{
    _pw->flush();
    _buffers.flush();
}

void
PageDict4MemWriter::addCounts(const std::string &word,
                              const PostingListCounts &counts)
{
    _pw->addCounts(word, counts);
}

}
