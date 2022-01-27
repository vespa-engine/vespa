// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include "compression.h"
#include <vespa/searchlib/index/docidandfeatures.h>

#define K_VALUE_POSOCC_FIRST_WORDPOS 8

#define K_VALUE_POSOCC_DELTA_WORDPOS 4

// Compression parameters for EGPosOcc encode/decode context
#define K_VALUE_POSOCC_ELEMENTLEN 9
#define K_VALUE_POSOCC_NUMPOSITIONS 0

#define K_VALUE_POSOCC_NUMELEMENTS 0
#define K_VALUE_POSOCC_ELEMENTID 0
#define K_VALUE_POSOCC_ELEMENTWEIGHT 9

namespace search::bitcompression {

class PosOccFieldsParams;

template <bool bigEndian>
class EG2PosOccDecodeContext : public FeatureDecodeContext<bigEndian>
{
public:
    typedef FeatureDecodeContext<bigEndian> ParentClass;
    using ParentClass::smallAlign;
    using ParentClass::readBits;
    using ParentClass::_valI;
    using ParentClass::_val;
    using ParentClass::_cacheInt;
    using ParentClass::_preRead;
    using ParentClass::_valE;
    using ParentClass::_fileReadBias;
    using ParentClass::_readContext;
    using ParentClass::readHeader;
    typedef EncodeContext64<bigEndian> EC;
    typedef index::PostingListParams PostingListParams;

    const PosOccFieldsParams *_fieldsParams;

    EG2PosOccDecodeContext(const PosOccFieldsParams *fieldsParams)
        : FeatureDecodeContext<bigEndian>(),
          _fieldsParams(fieldsParams)
    {
    }

    EG2PosOccDecodeContext(const uint64_t *compr, int bitOffset,
                           const PosOccFieldsParams *fieldsParams)
        : FeatureDecodeContext<bigEndian>(compr, bitOffset),
          _fieldsParams(fieldsParams)
    {
    }


    EG2PosOccDecodeContext(const uint64_t *compr,
                           int bitOffset,
                           uint64_t bitLength,
                           const PosOccFieldsParams *fieldsParams)
        : FeatureDecodeContext<bigEndian>(compr, bitOffset, bitLength),
          _fieldsParams(fieldsParams)
    {
    }


    EG2PosOccDecodeContext &
    operator=(const EG2PosOccDecodeContext &rhs)
    {
        FeatureDecodeContext<bigEndian>::operator=(rhs);
        _fieldsParams = rhs._fieldsParams;
        return *this;
    }

    void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix) override;
    const vespalib::string &getIdentifier() const override;
    void readFeatures(search::index::DocIdAndFeatures &features) override;
    void skipFeatures(unsigned int count) override;
    void unpackFeatures(const search::fef::TermFieldMatchDataArray &matchData, uint32_t docId) override;
    void setParams(const PostingListParams &params) override;
    void getParams(PostingListParams &params) const override;
};


template <bool bigEndian>
class EG2PosOccDecodeContextCooked : public EG2PosOccDecodeContext<bigEndian>
{
public:
    typedef EG2PosOccDecodeContext<bigEndian> ParentClass;
    using ParentClass::smallAlign;
    using ParentClass::readBits;
    using ParentClass::_valI;
    using ParentClass::_val;
    using ParentClass::_cacheInt;
    using ParentClass::_preRead;
    using ParentClass::_valE;
    using ParentClass::_fileReadBias;
    using ParentClass::_readContext;
    using ParentClass::_fieldsParams;
    typedef EncodeContext64<bigEndian> EC;
    typedef index::PostingListParams PostingListParams;

    EG2PosOccDecodeContextCooked(const PosOccFieldsParams *fieldsParams)
        : EG2PosOccDecodeContext<bigEndian>(fieldsParams)
    {
    }

    EG2PosOccDecodeContextCooked(const uint64_t *compr, int bitOffset,
                                 const PosOccFieldsParams *fieldsParams)
        : EG2PosOccDecodeContext<bigEndian>(compr, bitOffset, fieldsParams)
    {
    }


    EG2PosOccDecodeContextCooked(const uint64_t *compr,
                                 int bitOffset,
                                 uint64_t bitLength,
                                 const PosOccFieldsParams *fieldsParams)
        : EG2PosOccDecodeContext<bigEndian>(compr, bitOffset, bitLength,
                fieldsParams)
    {
    }


    EG2PosOccDecodeContextCooked &
    operator=(const EG2PosOccDecodeContext<bigEndian> &rhs)
    {
        EG2PosOccDecodeContext<bigEndian>::operator=(rhs);
        return *this;
    }

    void readFeatures(search::index::DocIdAndFeatures &features) override;
    void getParams(PostingListParams &params) const override;
};


template <bool bigEndian>
class EG2PosOccEncodeContext : public FeatureEncodeContext<bigEndian>
{
public:
    typedef FeatureEncodeContext<bigEndian> ParentClass;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListParams PostingListParams;
    using ParentClass::smallAlign;
    using ParentClass::writeBits;
    using ParentClass::_valI;
    using ParentClass::_valE;
    using ParentClass::_writeContext;
    using ParentClass::encodeExpGolomb;
    using ParentClass::readHeader;
    using ParentClass::writeHeader;

    const PosOccFieldsParams *_fieldsParams;

    EG2PosOccEncodeContext(const PosOccFieldsParams *fieldsParams)
        : FeatureEncodeContext<bigEndian>(),
          _fieldsParams(fieldsParams)
    {
    }

    EG2PosOccEncodeContext &
    operator=(const EG2PosOccEncodeContext &rhs)
    {
        FeatureEncodeContext<bigEndian>::operator=(rhs);
        _fieldsParams = rhs._fieldsParams;
        return *this;
    }

    void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix) override;
    void writeHeader(vespalib::GenericHeader &header, const vespalib::string &prefix) const override;
    const vespalib::string &getIdentifier() const override;
    void writeFeatures(const DocIdAndFeatures &features) override;
    void setParams(const PostingListParams &params) override;
    void getParams(PostingListParams &params) const override;
};


template <bool bigEndian>
class EGPosOccDecodeContext : public EG2PosOccDecodeContext<bigEndian>
{
public:
    typedef EG2PosOccDecodeContext<bigEndian> ParentClass;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListParams PostingListParams;
    using ParentClass::smallAlign;
    using ParentClass::readBits;
    using ParentClass::_valI;
    using ParentClass::_val;
    using ParentClass::_cacheInt;
    using ParentClass::_preRead;
    using ParentClass::_valE;
    using ParentClass::_fileReadBias;
    using ParentClass::_readContext;
    using ParentClass::_fieldsParams;
    using ParentClass::readHeader;
    typedef EncodeContext64<bigEndian> EC;

    EGPosOccDecodeContext(const PosOccFieldsParams *fieldsParams)
        : EG2PosOccDecodeContext<bigEndian>(fieldsParams)
    {
    }

    EGPosOccDecodeContext(const uint64_t *compr, int bitOffset,
                          const PosOccFieldsParams *fieldsParams)
        : EG2PosOccDecodeContext<bigEndian>(compr, bitOffset, fieldsParams)
    {
    }


    EGPosOccDecodeContext(const uint64_t *compr,
                          int bitOffset,
                          uint64_t bitLength,
                          const PosOccFieldsParams *fieldsParams)
        : EG2PosOccDecodeContext<bigEndian>(compr, bitOffset, bitLength,
                fieldsParams)
    {
    }


    EGPosOccDecodeContext &
    operator=(const EGPosOccDecodeContext &rhs)
    {
        EG2PosOccDecodeContext<bigEndian>::operator=(rhs);
        return *this;
    }

    void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix) override;
    const vespalib::string &getIdentifier() const override;
    void readFeatures(search::index::DocIdAndFeatures &features) override;
    void skipFeatures(unsigned int count) override;
    void unpackFeatures(const search::fef::TermFieldMatchDataArray &matchData, uint32_t docId) override;
    void setParams(const PostingListParams &params) override;
    void getParams(PostingListParams &params) const override;
};


template <bool bigEndian>
class EGPosOccDecodeContextCooked : public EGPosOccDecodeContext<bigEndian>
{
public:
    typedef EGPosOccDecodeContext<bigEndian> ParentClass;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListParams PostingListParams;
    using ParentClass::smallAlign;
    using ParentClass::readBits;
    using ParentClass::_valI;
    using ParentClass::_val;
    using ParentClass::_cacheInt;
    using ParentClass::_preRead;
    using ParentClass::_valE;
    using ParentClass::_fileReadBias;
    using ParentClass::_readContext;
    using ParentClass::_fieldsParams;
    typedef EncodeContext64<bigEndian> EC;

    EGPosOccDecodeContextCooked(const PosOccFieldsParams *fieldsParams)
        : EGPosOccDecodeContext<bigEndian>(fieldsParams)
    {
    }

    EGPosOccDecodeContextCooked(const uint64_t *compr, int bitOffset,
                                const PosOccFieldsParams *fieldsParams)
        : EGPosOccDecodeContext<bigEndian>(compr, bitOffset, fieldsParams)
    {
    }


    EGPosOccDecodeContextCooked(const uint64_t *compr,
                                int bitOffset,
                                uint64_t bitLength,
                                const PosOccFieldsParams *fieldsParams)
        : EGPosOccDecodeContext<bigEndian>(compr, bitOffset, bitLength,
                fieldsParams)
    {
    }


    EGPosOccDecodeContextCooked &
    operator=(const EGPosOccDecodeContext<bigEndian> &rhs)
    {
        EGPosOccDecodeContext<bigEndian>::operator=(rhs);
        return *this;
    }

    void readFeatures(search::index::DocIdAndFeatures &features) override;
    void getParams(PostingListParams &params) const override;
};


template <bool bigEndian>
class EGPosOccEncodeContext : public EG2PosOccEncodeContext<bigEndian>
{
public:
    typedef EG2PosOccEncodeContext<bigEndian> ParentClass;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListParams PostingListParams;
    using ParentClass::smallAlign;
    using ParentClass::writeBits;
    using ParentClass::_valI;
    using ParentClass::_valE;
    using ParentClass::_writeContext;
    using ParentClass::asmlog2;
    using ParentClass::encodeExpGolomb;
    using ParentClass::_fieldsParams;
    using ParentClass::readHeader;
    using ParentClass::writeHeader;

    EGPosOccEncodeContext(const PosOccFieldsParams *fieldsParams)
        : EG2PosOccEncodeContext<bigEndian>(fieldsParams)
    {
    }

    EGPosOccEncodeContext &
    operator=(const EGPosOccEncodeContext &rhs)
    {
        EG2PosOccEncodeContext<bigEndian>::operator=(rhs);
        return *this;
    }

    void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix) override;
    void writeHeader(vespalib::GenericHeader &header, const vespalib::string &prefix) const override;
    const vespalib::string &getIdentifier() const override;
    void writeFeatures(const DocIdAndFeatures &features) override;
    void setParams(const PostingListParams &params) override;
    void getParams(PostingListParams &params) const override;

    static uint32_t calcElementLenK(uint32_t avgElementLen) {
        return (avgElementLen < 4) ? 1u : (asmlog2(avgElementLen));
    }

    static uint32_t calcWordPosK(uint32_t numPositions, uint32_t elementLen) {
        uint32_t avgDelta = elementLen / (numPositions + 1);
        uint32_t wordPosK = (avgDelta < 4) ? 1 : (asmlog2(avgDelta));
        return wordPosK;
    }
};


extern template class EG2PosOccDecodeContext<true>;
extern template class EG2PosOccDecodeContext<false>;

extern template class EG2PosOccDecodeContextCooked<true>;
extern template class EG2PosOccDecodeContextCooked<false>;

extern template class EG2PosOccEncodeContext<true>;
extern template class EG2PosOccEncodeContext<false>;

extern template class EGPosOccDecodeContext<true>;
extern template class EGPosOccDecodeContext<false>;

extern template class EGPosOccDecodeContextCooked<true>;
extern template class EGPosOccDecodeContextCooked<false>;

extern template class EGPosOccEncodeContext<true>;
extern template class EGPosOccEncodeContext<false>;

}
