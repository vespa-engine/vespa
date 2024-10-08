// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace search::index::schema {

/**
 * Basic data type for a field.
 **/
enum class DataType {
    BOOL = 0,
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
    REFERENCE = 14,
    COMBINED = 15
};

/**
 * Collection type for a field.
 **/
enum class CollectionType {
    SINGLE = 0,
    ARRAY = 1,
    WEIGHTEDSET = 2
};

DataType dataTypeFromName(std::string_view name);
std::string getTypeName(DataType type);
std::ostream &operator<<(std::ostream &os, const DataType &type);

CollectionType collectionTypeFromName(std::string_view n);
std::string getTypeName(CollectionType type);
std::ostream &operator<<(std::ostream &os, const CollectionType &type);


}
