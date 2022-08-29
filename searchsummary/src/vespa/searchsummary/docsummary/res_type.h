// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::docsummary {

/**
 * This enumeration contains values denoting the different types of
 * docsum fields. NOTE: The internal implementation depends on RES_INT
 * having the value 0. All types < RES_STRING must be fixed size and
 * all types > RES_STRING must be variable size.
 **/
enum ResType {
    RES_INT = 0,
    RES_SHORT,
    RES_BOOL,
    RES_BYTE,
    RES_FLOAT,
    RES_DOUBLE,
    RES_INT64,
    RES_STRING,
    RES_DATA,
    RES_LONG_STRING,
    RES_LONG_DATA,
    RES_JSONSTRING,
    RES_TENSOR,
    RES_FEATUREDATA,
    RES_BAD
};

}
