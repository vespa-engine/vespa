// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4_mem_seq_reader.h"

namespace search::diskindex::test {

PageDict4MemSeqReader::PageDict4MemSeqReader(uint32_t chunkSize, uint64_t numWordIds,
                                             ThreeLevelCountWriteBuffers &wb)
    : _decoders(chunkSize, numWordIds),
      _buffers(_decoders.ssd, _decoders.spd, _decoders.pd, wb),
      _ssr(_buffers._rcssd,
           wb._ssHeaderLen, wb._ssFileBitSize,
           wb._spHeaderLen, wb._spFileBitSize,
           wb._pHeaderLen, wb._pFileBitSize),
      _pr(_ssr, _decoders.spd, _decoders.pd)
{
    _ssr.setup(_decoders.ssd);
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
