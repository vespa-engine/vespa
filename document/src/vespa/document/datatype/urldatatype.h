// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/datatype/structdatatype.h>

namespace document {

class UrlDataType {
private:
    UrlDataType() { /* hide */ }
    static StructDataType::UP createInstance();

public:
    static const vespalib::string STRUCT_NAME;
    static const int              STRUCT_VERSION;
    static const vespalib::string FIELD_ALL;
    static const vespalib::string FIELD_SCHEME;
    static const vespalib::string FIELD_HOST;
    static const vespalib::string FIELD_PORT;
    static const vespalib::string FIELD_PATH;
    static const vespalib::string FIELD_QUERY;
    static const vespalib::string FIELD_FRAGMENT;

    static const StructDataType &getInstance();
};

} // document

