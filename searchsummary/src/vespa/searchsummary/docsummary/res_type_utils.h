// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type.h"

namespace search::docsummary {


/*
 * Utilitiy functions for checking if result type is ok.
 */
struct ResTypeUtils
{
    /**
     * Determine if a result field type is of variable size.
     *
     * @return true for variable size field types, false for fixed
     * size field types
     **/
    static bool IsVariableSize(ResType t) { return (t >= RES_STRING); }


    /**
     * Determine if a pair of result field types are binary
     * compatible. A pair of types are binary compatible if the packed
     * representation is identical.
     *
     * @return true if the given types are binary compatible.
     * @param a enum value of a result field type.
     * @param b enum value of a result field type.
     **/
    static bool IsBinaryCompatible(ResType a, ResType b)
    {
        if (a == b) {
            return true;
        }
        switch (a) {
        case RES_BYTE:
        case RES_BOOL:
            return (b == RES_BYTE || b == RES_BOOL);
        case RES_STRING:
        case RES_DATA:
            return (b == RES_STRING || b == RES_DATA);
        case RES_LONG_STRING:
        case RES_LONG_DATA:
        case RES_FEATUREDATA:
        case RES_JSONSTRING:
            return (b == RES_LONG_STRING || b == RES_LONG_DATA ||
                    b == RES_FEATUREDATA || b == RES_JSONSTRING);
        default:
            return false;
        }
        return false;
    }


    /**
     * Determine if a pair of result field types are runtime
     * compatible. A pair of types are runtime compatible if the
     * unpacked (@ref ResEntry) representation is identical.
     *
     * @return true if the given types are runtime compatible.
     * @param a enum value of a result field type.
     * @param b enum value of a result field type.
     **/
    static bool IsRuntimeCompatible(ResType a, ResType b)
    {
        switch (a) {
        case RES_INT:
        case RES_SHORT:
        case RES_BYTE:
        case RES_BOOL:
            return (b == RES_INT || b == RES_SHORT || b == RES_BYTE || b == RES_BOOL);
        case RES_FLOAT:
        case RES_DOUBLE:
            return (b == RES_FLOAT || b == RES_DOUBLE);
        case RES_INT64:
            return b == RES_INT64;
        case RES_STRING:
        case RES_LONG_STRING:
        case RES_JSONSTRING:
            return (b == RES_STRING || b == RES_LONG_STRING || b == RES_JSONSTRING);
        case RES_DATA:
        case RES_LONG_DATA:
            return (b == RES_DATA || b == RES_LONG_DATA);
        case RES_TENSOR:
            return (b == RES_TENSOR);
        case RES_FEATUREDATA:
            return (b == RES_FEATUREDATA);
        }
        return false;
    }

    /**
     * @return the name of the given result field type.
     * @param resType enum value of a result field type.
     **/
    static const char *GetResTypeName(ResType type);
};

}
