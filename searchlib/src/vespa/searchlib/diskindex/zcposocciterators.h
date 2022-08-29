// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zcpostingiterators.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search::diskindex {

struct Zc4PostingParams;

template <bool bigEndian, bool dynamic_k>
class ZcRareWordPosOccIterator : public ZcRareWordPostingIterator<bigEndian, dynamic_k>
{
private:
    using ParentClass = ZcRareWordPostingIterator<bigEndian, dynamic_k>;
    using ParentClass::_decodeContext;

    using DecodeContextReal = std::conditional_t<dynamic_k, bitcompression::EGPosOccDecodeContextCooked<bigEndian>, bitcompression::EG2PosOccDecodeContextCooked<bigEndian>>;
    DecodeContextReal _decodeContextReal;
public:
    ZcRareWordPosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit,
                             bool decode_normal_features, bool decode_interleaved_features,
                             bool unpack_normal_features, bool unpack_interleaved_features,
                             const bitcompression::PosOccFieldsParams *fieldsParams,
                             fef::TermFieldMatchDataArray matchData);
};


template <bool bigEndian, bool dynamic_k>
class ZcPosOccIterator : public ZcPostingIterator<bigEndian>
{
private:
    typedef ZcPostingIterator<bigEndian> ParentClass;
    using ParentClass::_decodeContext;

    using DecodeContext = std::conditional_t<dynamic_k, bitcompression::EGPosOccDecodeContextCooked<bigEndian>, bitcompression::EG2PosOccDecodeContextCooked<bigEndian>>;
    DecodeContext _decodeContextReal;
public:
    ZcPosOccIterator(Position start, uint64_t bitLength, uint32_t docIdLimit,
                     bool decode_normal_features, bool decode_interleaved_features,
                     bool unpack_normal_features, bool unpack_interleaved_features,
                     uint32_t minChunkDocs, const index::PostingListCounts &counts,
                     const bitcompression::PosOccFieldsParams *fieldsParams,
                     fef::TermFieldMatchDataArray matchData);
};

std::unique_ptr<search::queryeval::SearchIterator>
create_zc_posocc_iterator(bool bigEndian, const index::PostingListCounts &counts, bitcompression::Position start, uint64_t bit_length, const Zc4PostingParams &posting_params, const bitcompression::PosOccFieldsParams &fields_params, fef::TermFieldMatchDataArray match_data);

extern template class ZcRareWordPosOccIterator<false, false>;
extern template class ZcRareWordPosOccIterator<false, true>;
extern template class ZcRareWordPosOccIterator<true, false>;
extern template class ZcRareWordPosOccIterator<true, true>;

extern template class ZcPosOccIterator<false, false>;
extern template class ZcPosOccIterator<false, true>;
extern template class ZcPosOccIterator<true, false>;
extern template class ZcPosOccIterator<true, true>;

}
