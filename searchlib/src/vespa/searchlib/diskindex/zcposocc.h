// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zcposting.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search {

namespace diskindex {

class Zc4PosOccSeqRead : public Zc4PostingSeqRead
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EG2PosOccDecodeContextCooked<true> _cookedDecodeContext;
    bitcompression::EG2PosOccDecodeContext<true> _rawDecodeContext;

public:
    Zc4PosOccSeqRead(index::PostingListCountFileSeqRead *countFile);
    void setFeatureParams(const PostingListParams &params) override;
    static const vespalib::string &getSubIdentifier();
};


class Zc4PosOccSeqWrite : public Zc4PostingSeqWrite
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EG2PosOccEncodeContext<true> _realEncodeFeatures;

public:
    typedef index::Schema Schema;

    Zc4PosOccSeqWrite(const Schema &schema, uint32_t indexId, index::PostingListCountFileSeqWrite *countFile);
};


class ZcPosOccSeqRead : public ZcPostingSeqRead
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EGPosOccDecodeContextCooked<true> _cookedDecodeContext;
    bitcompression::EGPosOccDecodeContext<true> _rawDecodeContext;
public:
    ZcPosOccSeqRead(index::PostingListCountFileSeqRead *countFile);
    void setFeatureParams(const PostingListParams &params) override;
    static const vespalib::string &getSubIdentifier();
};


class ZcPosOccSeqWrite : public ZcPostingSeqWrite
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EGPosOccEncodeContext<true> _realEncodeFeatures;
public:
    typedef index::Schema Schema;
    ZcPosOccSeqWrite(const Schema &schema, uint32_t indexId, index::PostingListCountFileSeqWrite *countFile);
};


} // namespace diskindex

} // namespace search

