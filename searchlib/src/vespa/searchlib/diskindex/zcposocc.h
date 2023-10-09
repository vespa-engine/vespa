// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zcposting.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>

namespace search::diskindex {

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
    const index::FieldLengthInfo &get_field_length_info() const override;
};


class Zc4PosOccSeqWrite : public Zc4PostingSeqWrite
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EG2PosOccEncodeContext<true> _realEncodeFeatures;

public:
    using Schema = index::Schema;

    Zc4PosOccSeqWrite(const Schema &schema, uint32_t indexId,
                      const index::FieldLengthInfo &field_length_info,
                      index::PostingListCountFileSeqWrite *countFile);
};


class ZcPosOccSeqRead : public Zc4PostingSeqRead
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EGPosOccDecodeContextCooked<true> _cookedDecodeContext;
    bitcompression::EGPosOccDecodeContext<true> _rawDecodeContext;
public:
    ZcPosOccSeqRead(index::PostingListCountFileSeqRead *countFile);
    void setFeatureParams(const PostingListParams &params) override;
    static const vespalib::string &getSubIdentifier();
    const index::FieldLengthInfo &get_field_length_info() const override;
};


class ZcPosOccSeqWrite : public ZcPostingSeqWrite
{
private:
    bitcompression::PosOccFieldsParams _fieldsParams;
    bitcompression::EGPosOccEncodeContext<true> _realEncodeFeatures;
public:
    using Schema = index::Schema;
    ZcPosOccSeqWrite(const Schema &schema, uint32_t indexId,
                     const index::FieldLengthInfo &field_length_info,
                     index::PostingListCountFileSeqWrite *countFile);
};

}
