// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>

class FastOS_FileInterface;

namespace vespalib { class DataBuffer; }

namespace search {

class FileRandRead
{
public:
    typedef std::shared_ptr<FastOS_FileInterface> FSP;
    virtual ~FileRandRead() { }
    virtual FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) = 0;
    virtual int64_t getSize() = 0;
};

}
