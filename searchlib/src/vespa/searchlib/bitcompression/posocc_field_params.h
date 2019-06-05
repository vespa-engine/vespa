// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vespa/vespalib/stllike/string.h>

namespace search::index {
class PostingListParams;
class Schema;
}

namespace vespalib { class GenericHeader; }

namespace search::bitcompression {

class PosOccFieldParams
{
public:
    typedef index::PostingListParams PostingListParams;
    typedef index::Schema Schema;

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
    vespalib::string _name;

    PosOccFieldParams();

    bool operator==(const PosOccFieldParams &rhs) const;
    static vespalib::string getParamsPrefix(uint32_t idx);
    void getParams(PostingListParams &params, uint32_t idx) const;
    void setParams(const PostingListParams &params, uint32_t idx);
    void setSchemaParams(const Schema &schema, uint32_t fieldId);
    void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix);
    void writeHeader(vespalib::GenericHeader &header, const vespalib::string &prefix) const;
};

}

