// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/datatype/structdatatype.h>
#include <vespa/vespalib/util/sync.h>

namespace document {

class PositionDataType {
private:
    static StructDataType::UP _instance;
    static vespalib::Lock     _lock;

    PositionDataType();
    static StructDataType::UP createInstance();

public:
    static const vespalib::string STRUCT_NAME;
    static const int              STRUCT_VERSION;
    static const vespalib::string FIELD_X;
    static const vespalib::string FIELD_Y;

    static const StructDataType &getInstance();
    static vespalib::string getZCurveFieldName(const vespalib::string &name);
    static vespalib::stringref cutZCurveFieldName(vespalib::stringref name);
    static bool isZCurveFieldName(vespalib::stringref name);
};

} // document

