// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/input.h>

namespace vespalib {

/**
 * Abstract input backed by a memory-mapped file.
 **/
class MappedFileInput : public Input
{
private:
    int     _fd;
    char   *_data;
    size_t  _size;
    size_t  _used;
public:
    MappedFileInput(const vespalib::string &file_name);
    ~MappedFileInput();
    bool valid() const;
    Memory get() const { return Memory(_data, _size); }
    Memory obtain() override;
    Input &evict(size_t bytes) override;
};

} // namespace vespalib
