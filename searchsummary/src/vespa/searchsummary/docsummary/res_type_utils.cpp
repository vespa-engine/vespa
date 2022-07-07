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
    }
    return "unknown-type";
}

}
