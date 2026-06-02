// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributememoryfilewriter.h"

#include "attributememoryfilebufferwriter.h"

#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search {

AttributeMemoryFileWriter::AttributeMemoryFileWriter() : IAttributeFileWriter(), _bufs() {
}

AttributeMemoryFileWriter::~AttributeMemoryFileWriter() = default;

AttributeMemoryFileWriter::Buffer AttributeMemoryFileWriter::allocBuf(size_t size) {
    return std::make_unique<BufferBuf>(size, FileSettings::DIRECTIO_ALIGNMENT);
}

void AttributeMemoryFileWriter::writeBuf(Buffer buf) {
    vespalib::TransientMemoryTracker tracker;
    tracker.set_transient_memory(buf->getDataLen());
    _bufs.emplace_back(std::make_pair(std::move(buf), std::move(tracker)));
}

void AttributeMemoryFileWriter::write_buf(Buffer buf, vespalib::TransientMemoryTracker tracker) {
    _bufs.emplace_back(std::make_pair(std::move(buf), std::move(tracker)));
}

void AttributeMemoryFileWriter::writeTo(IAttributeFileWriter& writer) {
    for (auto& buf : _bufs) {
        writer.write_buf(std::move(buf.first), std::move(buf.second));
    }
    _bufs.clear();
}

std::unique_ptr<BufferWriter> AttributeMemoryFileWriter::allocBufferWriter() {
    return std::make_unique<AttributeMemoryFileBufferWriter>(*this);
}

void AttributeMemoryFileWriter::close() {
}

} // namespace search
