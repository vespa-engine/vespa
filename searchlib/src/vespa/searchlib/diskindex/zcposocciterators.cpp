// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposocciterators.h"

namespace search::diskindex {

using search::fef::TermFieldMatchDataArray;
using search::bitcompression::PosOccFieldsParams;
using search::index::PostingListCounts;

#define DEBUG_ZCFILTEROCC_PRINTF 0
#define DEBUG_ZCFILTEROCC_ASSERT 0

template <bool bigEndian>
Zc4RareWordPosOccIterator<bigEndian>::
Zc4RareWordPosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit, bool decode_cheap_features,
                          const PosOccFieldsParams *fieldsParams,
                          const TermFieldMatchDataArray &matchData)
    : Zc4RareWordPostingIterator<bigEndian>(matchData, start, docIdLimit, decode_cheap_features),
      _decodeContextReal(start.getOccurences(), start.getBitOffset(), bitLength, fieldsParams)
{
    assert(!matchData.valid() || (fieldsParams->getNumFields() == matchData.size()));
    _decodeContext = &_decodeContextReal;
}


template <bool bigEndian>
Zc4PosOccIterator<bigEndian>::
Zc4PosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit, bool decode_cheap_features,
                  uint32_t minChunkDocs, const PostingListCounts &counts,
                  const PosOccFieldsParams *fieldsParams,
                  const TermFieldMatchDataArray &matchData)
    : ZcPostingIterator<bigEndian>(minChunkDocs, false, counts, matchData, start, docIdLimit, decode_cheap_features),
      _decodeContextReal(start.getOccurences(), start.getBitOffset(), bitLength, fieldsParams)
{
    assert(!matchData.valid() || (fieldsParams->getNumFields() == matchData.size()));
    _decodeContext = &_decodeContextReal;
}


template <bool bigEndian>
ZcRareWordPosOccIterator<bigEndian>::
ZcRareWordPosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit, bool decode_cheap_features,
                         const PosOccFieldsParams *fieldsParams,
                         const TermFieldMatchDataArray &matchData)
    : ZcRareWordPostingIterator<bigEndian>(matchData, start, docIdLimit, decode_cheap_features),
      _decodeContextReal(start.getOccurences(), start.getBitOffset(), bitLength, fieldsParams)
{
    assert(!matchData.valid() || (fieldsParams->getNumFields() == matchData.size()));
    _decodeContext = &_decodeContextReal;
}


template <bool bigEndian>
ZcPosOccIterator<bigEndian>::
ZcPosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit, bool decode_cheap_features,
                 uint32_t minChunkDocs, const PostingListCounts &counts,
                 const PosOccFieldsParams *fieldsParams,
                 const TermFieldMatchDataArray &matchData)
    : ZcPostingIterator<bigEndian>(minChunkDocs, true, counts, matchData, start, docIdLimit, decode_cheap_features),
      _decodeContextReal(start.getOccurences(), start.getBitOffset(), bitLength, fieldsParams)
{
    assert(!matchData.valid() || (fieldsParams->getNumFields() == matchData.size()));
    _decodeContext = &_decodeContextReal;
}


template class Zc4RareWordPosOccIterator<true>;
template class Zc4RareWordPosOccIterator<false>;

template class Zc4PosOccIterator<true>;
template class Zc4PosOccIterator<false>;

template class ZcRareWordPosOccIterator<true>;
template class ZcRareWordPosOccIterator<false>;

template class ZcPosOccIterator<true>;
template class ZcPosOccIterator<false>;

}
