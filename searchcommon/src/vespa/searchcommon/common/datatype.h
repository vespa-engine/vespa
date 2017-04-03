// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace index {
namespace schema {

/**
 * Basic data type for a field.
 **/
enum DataType { UINT1 = 0,
    UINT2 = 1,
    UINT4 = 2,
    INT8 = 3,
    INT16 = 4,
    INT32 = 5,
    INT64 = 6,
    FLOAT = 7,
    DOUBLE = 8,
    STRING = 9,
    RAW = 10,
    //FEATURE = 11,
    BOOLEANTREE = 12,
    TENSOR = 13,
    REFERENCE = 14
};

/**
 * Collection type for a field.
 **/
enum CollectionType { SINGLE = 0,
    ARRAY = 1,
    WEIGHTEDSET = 2
};

DataType dataTypeFromName(const vespalib::stringref &name);
vespalib::string getTypeName(DataType type);
CollectionType collectionTypeFromName(const vespalib::stringref &n);
vespalib::string getTypeName(CollectionType type);

}
}
}
