// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "uri_field.h"

namespace search::index {

/**
 * Fields from an index schema to be used for indexing
 **/
class SchemaIndexFields {
public:
    using FieldIdVector = std::vector<uint32_t>;
    using UriFieldIdVector = std::vector<UriField>;
    FieldIdVector _textFields;
    UriFieldIdVector _uriFields;
    
    SchemaIndexFields();
    ~SchemaIndexFields();
    void setup(const Schema &schema);
};

}
