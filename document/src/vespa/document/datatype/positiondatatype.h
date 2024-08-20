// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/datatype/structdatatype.h>

namespace document {

class PositionDataType {
private:
    PositionDataType();
    static StructDataType::UP createInstance();

public:
    static const std::string STRUCT_NAME;
    static const int              STRUCT_VERSION;
    static const std::string FIELD_X;
    static const std::string FIELD_Y;

    static const StructDataType &getInstance();
    static std::string getZCurveFieldName(const std::string &name);
    static std::string_view cutZCurveFieldName(std::string_view name);
    static bool isZCurveFieldName(std::string_view name);
};

} // document

