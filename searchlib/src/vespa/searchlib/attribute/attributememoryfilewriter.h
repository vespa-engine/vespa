// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributefilewriter.h"

#include <vespa/vespalib/util/transient_memory_tracker.h>

#include <vector>

namespace search {

/*
 * Class to write to a memory buffer representation of a single
 * attribute vector file (without header). Used by AttributeMemorySaveTarget.
 */
class AttributeMemoryFileWriter : public IAttributeFileWriter {
    std::vector<std::pair<Buffer, vespalib::TransientMemoryTracker>> _bufs;

public:
    AttributeMemoryFileWriter();
    ~AttributeMemoryFileWriter() override;
    Buffer allocBuf(size_t size) override;
    void writeBuf(Buffer buf) override;
    void write_buf(Buffer buf, vespalib::TransientMemoryTracker tracker) override;
    std::unique_ptr<BufferWriter> allocBufferWriter() override;
    void writeTo(IAttributeFileWriter& writer);
    void close() override;
};

} // namespace search
