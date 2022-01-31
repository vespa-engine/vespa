// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposocc.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/postinglistcountfile.h>
#include <vespa/searchlib/index/postinglistparams.h>

namespace search::diskindex {

using search::bitcompression::PosOccFieldsParams;
using search::bitcompression::EG2PosOccDecodeContext;
using search::bitcompression::EGPosOccDecodeContext;
using search::index::FieldLengthInfo;
using search::index::PostingListCountFileSeqRead;
using search::index::PostingListCountFileSeqWrite;

Zc4PosOccSeqRead::Zc4PosOccSeqRead(PostingListCountFileSeqRead *countFile)
    : Zc4PostingSeqRead(countFile, false),
      _fieldsParams(),
      _cookedDecodeContext(&_fieldsParams),
      _rawDecodeContext(&_fieldsParams)
{
    _reader.set_decode_features(&_cookedDecodeContext);
}


void
Zc4PosOccSeqRead::
setFeatureParams(const PostingListParams &params)
{
    bool oldCooked = &_reader.get_decode_features() == &_cookedDecodeContext;
    bool newCooked = oldCooked;
    params.get("cooked", newCooked);
    if (oldCooked != newCooked) {
        if (newCooked) {
            _cookedDecodeContext = _rawDecodeContext;
            _reader.set_decode_features(&_cookedDecodeContext);
        } else {
            _rawDecodeContext = _cookedDecodeContext;
            _reader.set_decode_features(&_rawDecodeContext);
        }
    }
}


const vespalib::string &
Zc4PosOccSeqRead::getSubIdentifier()
{
    PosOccFieldsParams fieldsParams;
    EG2PosOccDecodeContext<true> d(&fieldsParams);
    return d.getIdentifier();
}

const FieldLengthInfo &
Zc4PosOccSeqRead::get_field_length_info() const
{
    return _fieldsParams.getFieldParams()->get_field_length_info();
}

Zc4PosOccSeqWrite::Zc4PosOccSeqWrite(const Schema &schema,
                                     uint32_t indexId,
                                     const FieldLengthInfo &field_length_info,
                                     PostingListCountFileSeqWrite *countFile)
    : Zc4PostingSeqWrite(countFile),
      _fieldsParams(),
      _realEncodeFeatures(&_fieldsParams)
{
    _writer.set_encode_features(&_realEncodeFeatures);
    _fieldsParams.setSchemaParams(schema, indexId);
    _fieldsParams.set_field_length_info(field_length_info);
}


ZcPosOccSeqRead::ZcPosOccSeqRead(PostingListCountFileSeqRead *countFile)
    : Zc4PostingSeqRead(countFile, true),
      _fieldsParams(),
      _cookedDecodeContext(&_fieldsParams),
      _rawDecodeContext(&_fieldsParams)
{
    _reader.set_decode_features(&_cookedDecodeContext);
}


void
ZcPosOccSeqRead::
setFeatureParams(const PostingListParams &params)
{
    bool oldCooked = &_reader.get_decode_features() == &_cookedDecodeContext;
    bool newCooked = oldCooked;
    params.get("cooked", newCooked);
    if (oldCooked != newCooked) {
        if (newCooked) {
            _cookedDecodeContext = _rawDecodeContext;
            _reader.set_decode_features(&_cookedDecodeContext);
        } else {
            _rawDecodeContext = _cookedDecodeContext;
            _reader.set_decode_features(&_rawDecodeContext);
        }
    }
}


const vespalib::string &
ZcPosOccSeqRead::getSubIdentifier()
{
    PosOccFieldsParams fieldsParams;
    EGPosOccDecodeContext<true> d(&fieldsParams);
    return d.getIdentifier();
}

const FieldLengthInfo &
ZcPosOccSeqRead::get_field_length_info() const
{
    return _fieldsParams.getFieldParams()->get_field_length_info();
}

ZcPosOccSeqWrite::ZcPosOccSeqWrite(const Schema &schema,
                                   uint32_t indexId,
                                   const FieldLengthInfo &field_length_info,
                                   PostingListCountFileSeqWrite *countFile)
    : ZcPostingSeqWrite(countFile),
      _fieldsParams(),
      _realEncodeFeatures(&_fieldsParams)
{
    _writer.set_encode_features(&_realEncodeFeatures);
    _fieldsParams.setSchemaParams(schema, indexId);
    _fieldsParams.set_field_length_info(field_length_info);
}

}
