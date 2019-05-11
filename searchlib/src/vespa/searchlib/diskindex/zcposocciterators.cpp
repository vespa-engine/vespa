// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposocciterators.h"
#include "zc4_posting_params.h"

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

template <bool bigEndian>
std::unique_ptr<search::queryeval::SearchIterator>
create_zc_posocc_iterator(const PostingListCounts &counts, bitcompression::Position start, uint64_t bit_length, const Zc4PostingParams &posting_params, const bitcompression::PosOccFieldsParams &fields_params, const fef::TermFieldMatchDataArray &match_data)
{
    using EC = bitcompression::EncodeContext64<bigEndian>;
    bitcompression::DecodeContext64<bigEndian> d(start.getOccurences(), start.getBitOffset());
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
    uint32_t num_docs = static_cast<uint32_t>(val64) + 1;
    assert((num_docs == counts._numDocs) || ((num_docs == posting_params._min_chunk_docs) && (num_docs < counts._numDocs)));
    if (num_docs < posting_params._min_skip_docs) {
        if (posting_params._dynamic_k) {
            return std::make_unique<ZcRareWordPosOccIterator<bigEndian>>(start, bit_length, posting_params._doc_id_limit, posting_params._encode_cheap_features, &fields_params, match_data);
        } else {
            return std::make_unique<Zc4RareWordPosOccIterator<bigEndian>>(start, bit_length, posting_params._doc_id_limit, posting_params._encode_cheap_features, &fields_params, match_data);
        }
    } else {
        if (posting_params._dynamic_k) {
            return std::make_unique<ZcPosOccIterator<bigEndian>>(start, bit_length, posting_params._doc_id_limit, posting_params._encode_cheap_features, posting_params._min_chunk_docs, counts, &fields_params, match_data);
        } else {
            return std::make_unique<Zc4PosOccIterator<bigEndian>>(start, bit_length, posting_params._doc_id_limit, posting_params._encode_cheap_features, posting_params._min_chunk_docs, counts, &fields_params, match_data);
        }
    }
}

std::unique_ptr<search::queryeval::SearchIterator>
create_zc_posocc_iterator(bool bigEndian, const PostingListCounts &counts, bitcompression::Position start, uint64_t bit_length, const Zc4PostingParams &posting_params, const bitcompression::PosOccFieldsParams &fields_params, const fef::TermFieldMatchDataArray &match_data)
{
    if (bigEndian) {
        return create_zc_posocc_iterator<true>(counts, start, bit_length, posting_params, fields_params, match_data);
    } else {
        return create_zc_posocc_iterator<false>(counts, start, bit_length, posting_params, fields_params, match_data);
    }
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
