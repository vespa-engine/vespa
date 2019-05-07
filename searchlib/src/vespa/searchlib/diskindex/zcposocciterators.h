// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zcpostingiterators.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search::diskindex {

template <bool bigEndian>
class Zc4RareWordPosOccIterator : public Zc4RareWordPostingIterator<bigEndian>
{
private:
    typedef Zc4RareWordPostingIterator<bigEndian> ParentClass;
    using ParentClass::_decodeContext;

    typedef bitcompression::EG2PosOccDecodeContextCooked<bigEndian> DecodeContextReal;
    DecodeContextReal _decodeContextReal;
public:
    Zc4RareWordPosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit, bool decode_cheap_features,
                              const bitcompression::PosOccFieldsParams *fieldsParams,
                              const fef::TermFieldMatchDataArray &matchData);
};


template <bool bigEndian>
class Zc4PosOccIterator : public ZcPostingIterator<bigEndian>
{
private:
    typedef ZcPostingIterator<bigEndian> ParentClass;
    using ParentClass::_decodeContext;

    typedef bitcompression::EG2PosOccDecodeContextCooked<bigEndian> DecodeContext;
    DecodeContext _decodeContextReal;
public:
    Zc4PosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit, bool decode_cheap_features,
                      uint32_t minChunkDocs, const index::PostingListCounts &counts,
                      const bitcompression::PosOccFieldsParams *fieldsParams,
                      const fef::TermFieldMatchDataArray &matchData);
};


template <bool bigEndian>
class ZcRareWordPosOccIterator : public ZcRareWordPostingIterator<bigEndian>
{
private:
    typedef ZcRareWordPostingIterator<bigEndian> ParentClass;
    using ParentClass::_decodeContext;

    typedef bitcompression::EGPosOccDecodeContextCooked<bigEndian> DecodeContextReal;
    DecodeContextReal _decodeContextReal;
public:
    ZcRareWordPosOccIterator(Position start, uint64_t bitLength, uint32_t docidLimit, bool decode_cheap_features,
                             const bitcompression::PosOccFieldsParams *fieldsParams,
                             const fef::TermFieldMatchDataArray &matchData);
};


template <bool bigEndian>
class ZcPosOccIterator : public ZcPostingIterator<bigEndian>
{
private:
    typedef ZcPostingIterator<bigEndian> ParentClass;
    using ParentClass::_decodeContext;

    typedef bitcompression::EGPosOccDecodeContextCooked<bigEndian> DecodeContext;
    DecodeContext _decodeContextReal;
public:
    ZcPosOccIterator(Position start, uint64_t bitLength, uint32_t docidLimit, bool decode_cheap_features,
                     uint32_t minChunkDocs, const index::PostingListCounts &counts,
                     const bitcompression::PosOccFieldsParams *fieldsParams,
                     const fef::TermFieldMatchDataArray &matchData);
};


extern template class Zc4RareWordPosOccIterator<true>;
extern template class Zc4RareWordPosOccIterator<false>;

extern template class Zc4PosOccIterator<true>;
extern template class Zc4PosOccIterator<false>;

extern template class ZcRareWordPosOccIterator<true>;
extern template class ZcRareWordPosOccIterator<false>;

extern template class ZcPosOccIterator<true>;
extern template class ZcPosOccIterator<false>;

}
