// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4_mem_writer.h"
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search::diskindex::test {

PageDict4MemWriter::PageDict4MemWriter(EC &sse,
                                       EC &spe,
                                       EC &pe)
    : ThreeLevelCountWriteBuffers(sse, spe, pe),
      _ssw(NULL),
      _spw(NULL),
      _pw(NULL)
{
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
    _ssw = new PageDict4SSWriter(_sse);
    _spw = new PageDict4SPWriter(*_ssw, _spe);
    _pw = new PageDict4PWriter(*_spw, _pe);
    _spw->setup();
    _pw->setup();
}

void
PageDict4MemWriter::flush()
{
    _pw->flush();
    ThreeLevelCountWriteBuffers::flush();
}

void
PageDict4MemWriter::addCounts(const std::string &word,
                              const PostingListCounts &counts)
{
    _pw->addCounts(word, counts);
}

}
