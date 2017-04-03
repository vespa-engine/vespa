// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datatype.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/arraysize.h>

namespace search {
namespace index {
namespace schema {

using config::InvalidConfigException;

DataType
dataTypeFromName(const vespalib::stringref &name) {
    if      (name == "UINT1")   { return UINT1; }
    else if (name == "UINT2")   { return UINT2; }
    else if (name == "UINT4")   { return UINT4; }
    else if (name == "INT8")    { return INT8; }
    else if (name == "INT16")   { return INT16; }
    else if (name == "INT32")   { return INT32; }
    else if (name == "INT64")   { return INT64; }
    else if (name == "FLOAT")   { return FLOAT; }
    else if (name == "DOUBLE")  { return DOUBLE; }
    else if (name == "STRING")  { return STRING; }
    else if (name == "RAW")     { return RAW; }
    else if (name == "BOOLEANTREE") { return BOOLEANTREE; }
    else if (name == "TENSOR") { return TENSOR; }
    else if (name == "REFERENCE") { return REFERENCE; }
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
    if (type > vespalib::arraysize(datatype_str)) {
        vespalib::asciistream ost;
        ost << "UNKNOWN(" << type << ")";
        return ost.str();
    }
    return datatype_str[type];
}

CollectionType
collectionTypeFromName(const vespalib::stringref &name) {
    if (name == "SINGLE") { return SINGLE; }
    else if (name == "ARRAY") { return ARRAY; }
    else if (name == "WEIGHTEDSET") { return WEIGHTEDSET; }
    else {
        throw InvalidConfigException("Illegal enum value '" + name + "'");
    }
}

const char *collectiontype_str[] = { "SINGLE",
                                     "ARRAY",
                                     "WEIGHTEDSET" };

vespalib::string
getTypeName(CollectionType type) {
    if (type > vespalib::arraysize(collectiontype_str)) {
        vespalib::asciistream ost;
        ost << "UNKNOWN(" << type << ")";
        return ost.str();
    }
    return collectiontype_str[type];
}


}
}
}
