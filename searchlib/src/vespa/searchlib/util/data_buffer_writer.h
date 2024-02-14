// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bufferwriter.h"

namespace vespalib { class DataBuffer; }

namespace search {

/**
 * Class to write to a data buffer, used during migration of
 * attribute vector saver implementation.
 */
class DataBufferWriter : public BufferWriter
{
    vespalib::DataBuffer& _data_buffer;
public:
    DataBufferWriter(vespalib::DataBuffer& data_buffer);
    ~DataBufferWriter();
    void flush() override;
};

}
