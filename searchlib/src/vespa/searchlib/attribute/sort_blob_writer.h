// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/*
 * Base class for sort blob writers. Contains definition of tags
 * describing if value is present (any present value sorts before
 * a missing value).
 */
class SortBlobWriter {
public:
    /*
     * Missing value is always sorted last.
     */
    static constexpr unsigned char has_value = 0;
    static constexpr unsigned char missing_value = 1;
};

}
