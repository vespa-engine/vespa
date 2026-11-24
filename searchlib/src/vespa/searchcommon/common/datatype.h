// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace search::index::schema {

/**
 * Basic data type for a field.
 **/
enum class DataType {
    BOOL,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT,
    DOUBLE,
    STRING,
    RAW,
    BOOLEANTREE,
    TENSOR,
    REFERENCE,
    COMBINED
};

/**
 * Collection type for a field.
 **/
enum class CollectionType { SINGLE = 0, ARRAY = 1, WEIGHTEDSET = 2 };

DataType dataTypeFromName(std::string_view name);
std::string getTypeName(DataType type);
std::ostream& operator<<(std::ostream& os, const DataType& type);

CollectionType collectionTypeFromName(std::string_view n);
std::string getTypeName(CollectionType type);
std::ostream& operator<<(std::ostream& os, const CollectionType& type);

} // namespace search::index::schema
