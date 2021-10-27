// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posocc_field_params.h"
#include <vector>
#include <cassert>

namespace search::bitcompression {

class PosOccFieldsParams
{
    // Cache pointers.
    uint32_t _numFields;
    const PosOccFieldParams *_fieldParams;

    // Storage
    std::vector<PosOccFieldParams> _params;

public:
    typedef index::PostingListParams PostingListParams;
    typedef index::Schema Schema;

    PosOccFieldsParams();
    PosOccFieldsParams(const PosOccFieldsParams &rhs);

    PosOccFieldsParams &operator=(const PosOccFieldsParams &rhs);
    bool operator==(const PosOccFieldsParams &rhs) const;

    void cacheParamsRef() {
        _numFields = _params.size();
        _fieldParams = _params.empty() ? nullptr : &_params[0];
    }

    void assertCachedParamsRef() const {
        assert(_numFields == _params.size());
        assert(_fieldParams == (_params.empty() ? nullptr : &_params[0]));
    }

    uint32_t getNumFields() const { return _numFields; }
    const PosOccFieldParams *getFieldParams() const { return _fieldParams; }
    void getParams(PostingListParams &params) const;
    void setParams(const PostingListParams &params);
    void setSchemaParams(const Schema &schema, const uint32_t indexId);
    void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix);
    void writeHeader(vespalib::GenericHeader &header, const vespalib::string &prefix) const;
    void set_field_length_info(const index::FieldLengthInfo &field_length_info);
};

}
