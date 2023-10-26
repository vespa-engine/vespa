// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::bmcluster {

/*
 * Range of document "keys" used to generate documents
 */
class BmRange
{
    uint32_t _start;
    uint32_t _end;
public:
    BmRange(uint32_t start_in, uint32_t end_in)
        : _start(start_in),
          _end(end_in)
    {
    }
    uint32_t get_start() const { return _start; }
    uint32_t get_end() const { return _end; }
};

}
