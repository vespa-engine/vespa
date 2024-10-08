// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/field_length_info.h>
#include <cstdint>
#include <string>

namespace search::index {
class PostingListParams;
class Schema;
}

namespace vespalib { class GenericHeader; }

namespace search::bitcompression {

class PosOccFieldParams
{
public:
    using PostingListParams = index::PostingListParams;
    using Schema = index::Schema;

    enum CollectionType
    {
        SINGLE,
        ARRAY,
        WEIGHTEDSET
    };

    uint8_t _elemLenK;
    bool    _hasElements;
    bool    _hasElementWeights;
    uint32_t _avgElemLen;
    CollectionType _collectionType;
    std::string _name;
    index::FieldLengthInfo _field_length_info;

    PosOccFieldParams();

    bool operator==(const PosOccFieldParams &rhs) const;
    static std::string getParamsPrefix(uint32_t idx);
    void getParams(PostingListParams &params, uint32_t idx) const;
    void setParams(const PostingListParams &params, uint32_t idx);
    void setSchemaParams(const Schema &schema, uint32_t fieldId);
    void readHeader(const vespalib::GenericHeader &header, const std::string &prefix);
    void writeHeader(vespalib::GenericHeader &header, const std::string &prefix) const;
    const index::FieldLengthInfo &get_field_length_info() const { return _field_length_info; }
    void set_field_length_info(const index::FieldLengthInfo &field_length_info) { _field_length_info = field_length_info; }
};

}

