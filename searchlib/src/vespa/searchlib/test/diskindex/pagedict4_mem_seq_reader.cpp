// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4_mem_seq_reader.h"

namespace search::diskindex::test {

PageDict4MemSeqReader::PageDict4MemSeqReader(DC &ssd,
                                             DC &spd,
                                             DC &pd,
                                             ThreeLevelCountWriteBuffers &wb)
    : ThreeLevelCountReadBuffers(ssd, spd, pd, wb),
      _ssr(_rcssd,
           wb._ssHeaderLen, wb._ssFileBitSize,
           wb._spHeaderLen, wb._spFileBitSize,
           wb._pHeaderLen, wb._pFileBitSize),
      _pr(_ssr, spd, pd)
{
    _ssr.setup(ssd);
    _pr.setup();
}

PageDict4MemSeqReader::~PageDict4MemSeqReader() = default;

void
PageDict4MemSeqReader::readCounts(vespalib::string &word,
                                  uint64_t &wordNum,
                                  PostingListCounts &counts)
{
    _pr.readCounts(word, wordNum, counts);
}

}
