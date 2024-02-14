// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_buffer_writer.h"
#include <vespa/vespalib/data/databuffer.h>

namespace search {

namespace {

constexpr size_t buffer_size = 4_Ki;

}

DataBufferWriter::DataBufferWriter(vespalib::DataBuffer& data_buffer)
    : _data_buffer(data_buffer)
{
    _data_buffer.ensureFree(buffer_size);
    setup(_data_buffer.getFree(), _data_buffer.getFreeLen());
}

DataBufferWriter::~DataBufferWriter() = default;

void
DataBufferWriter::flush()
{
    if (usedLen() > 0) {
        _data_buffer.moveFreeToData(usedLen());
        _data_buffer.ensureFree(buffer_size);
        setup(_data_buffer.getFree(), _data_buffer.getFreeLen());
    }
}

}
