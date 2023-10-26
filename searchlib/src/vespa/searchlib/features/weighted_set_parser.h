// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::features {

/**
 * Utility for parsing a string representation of a weighted set
 * that is typically passed down with the query.
 *
 * The format of the weighted set is as follows:
 * {key1:weight1,key2:weight2,...,keyN:weightN}.
 */
class WeightedSetParser
{
public:
    template <typename OutputType>
    static void parse(const vespalib::string &input, OutputType &output);
};

}
