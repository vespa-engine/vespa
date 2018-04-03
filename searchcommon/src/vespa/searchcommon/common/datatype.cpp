// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datatype.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/arraysize.h>

namespace search::index::schema {

using config::InvalidConfigException;

DataType
dataTypeFromName(const vespalib::stringref &name) {
    if      (name == "UINT1")   { return DataType::UINT1; }
    else if (name == "UINT2")   { return DataType::UINT2; }
    else if (name == "UINT4")   { return DataType::UINT4; }
    else if (name == "INT8")    { return DataType::INT8; }
    else if (name == "INT16")   { return DataType::INT16; }
    else if (name == "INT32")   { return DataType::INT32; }
    else if (name == "INT64")   { return DataType::INT64; }
    else if (name == "FLOAT")   { return DataType::FLOAT; }
    else if (name == "DOUBLE")  { return DataType::DOUBLE; }
    else if (name == "STRING")  { return DataType::STRING; }
    else if (name == "RAW")     { return DataType::RAW; }
    else if (name == "BOOLEANTREE") { return DataType::BOOLEANTREE; }
    else if (name == "TENSOR") { return DataType::TENSOR; }
    else if (name == "REFERENCE") { return DataType::REFERENCE; }
    else {
        throw InvalidConfigException("Illegal enum value '" + name + "'");
    }
}

const char *datatype_str[] = { "UINT1",
                               "UINT2",
                               "UINT4",
                               "INT8",
                               "INT16",
                               "INT32",
                               "INT64",
                               "FLOAT",
                               "DOUBLE",
                               "STRING",
                               "RAW",
                               "FEATURE_NOTUSED",
                               "BOOLEANTREE",
                               "TENSOR",
                               "REFERENCE"};

vespalib::string
getTypeName(DataType type) {
    size_t typeAsNum = static_cast<size_t>(type);
    if (typeAsNum > vespalib::arraysize(datatype_str)) {
        vespalib::asciistream ost;
        ost << "UNKNOWN(" << typeAsNum << ")";
        return ost.str();
    }
    return datatype_str[typeAsNum];
}

std::ostream &
operator<<(std::ostream &os, const DataType &type)
{
    os << getTypeName(type);
    return os;
}

CollectionType
collectionTypeFromName(const vespalib::stringref &name) {
    if (name == "SINGLE") { return CollectionType::SINGLE; }
    else if (name == "ARRAY") { return CollectionType::ARRAY; }
    else if (name == "WEIGHTEDSET") { return CollectionType::WEIGHTEDSET; }
    else {
        throw InvalidConfigException("Illegal enum value '" + name + "'");
    }
}

const char *collectiontype_str[] = { "SINGLE",
                                     "ARRAY",
                                     "WEIGHTEDSET" };

vespalib::string
getTypeName(CollectionType type) {
    size_t typeAsNum = static_cast<size_t>(type);
    if (typeAsNum > vespalib::arraysize(collectiontype_str)) {
        vespalib::asciistream ost;
        ost << "UNKNOWN(" << typeAsNum << ")";
        return ost.str();
    }
    return collectiontype_str[typeAsNum];
}

std::ostream &
operator<<(std::ostream &os, const CollectionType &type)
{
    os << getTypeName(type);
    return os;
}

}
