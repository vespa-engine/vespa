// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "res_type_utils.h"

namespace search::docsummary {

const char *
ResTypeUtils::GetResTypeName(ResType type)
{
    switch (type) {
    case RES_INT:         return "integer";
    case RES_SHORT:       return "short";
    case RES_BYTE:        return "byte";
    case RES_BOOL:        return "bool";
    case RES_FLOAT:       return "float";
    case RES_DOUBLE:      return "double";
    case RES_INT64:       return "int64";
    case RES_STRING:      return "string";
    case RES_DATA:        return "data";
    case RES_LONG_STRING: return "longstring";
    case RES_LONG_DATA:   return "longdata";
    case RES_JSONSTRING:  return "jsonstring";
    case RES_TENSOR:      return "tensor";
    case RES_FEATUREDATA: return "featuredata";
    default:              return "unknown-type";
    }
}

ResType
ResTypeUtils::get_res_type(vespalib::stringref name)
{
    if (name == "integer") {
        return RES_INT;
    }
    if (name == "short") {
        return RES_SHORT;
    }
    if (name == "byte") {
        return RES_BYTE;
    }
    if (name == "bool") {
        return RES_BOOL;
    }
    if (name == "float") {
        return RES_FLOAT;
    }
    if (name == "double") {
        return RES_DOUBLE;
    }
    if (name == "int64") {
        return RES_INT64;
    }
    if (name == "string") {
        return RES_STRING;
    }
    if (name == "data") {
        return RES_DATA;
    }
    if (name == "longstring") {
        return RES_LONG_STRING;
    }
    if (name == "longdata") {
        return RES_LONG_DATA;
    }
    if (name == "jsonstring") {
        return RES_JSONSTRING;
    }
    if (name == "tensor") {
        return RES_TENSOR;
    }
    if (name == "featuredata") {
        return RES_FEATUREDATA;
    }
    // Known aliases
    if (name == "raw") {
        return RES_DATA;
    }
    if (name == "xmlstring") {
        return RES_JSONSTRING;
    }
    return RES_BAD;
}

}
