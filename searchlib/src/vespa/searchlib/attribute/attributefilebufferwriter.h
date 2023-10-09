// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributefilewriter.h"
#include <vespa/searchlib/util/bufferwriter.h>

namespace search {

/*
 * BufferWriter implementation that passes full buffers on to
 * IAttributeFileWriter.
 */
class AttributeFileBufferWriter : public BufferWriter
{
protected:
    using BufferBuf = IAttributeFileWriter::BufferBuf;
    using Buffer = IAttributeFileWriter::Buffer;
    Buffer _buf;
    size_t _bytesWritten;
    uint32_t _incompleteBuffers;
    IAttributeFileWriter &_fileWriter;

    virtual void onFlush(size_t nowLen) = 0;
public:
    static constexpr size_t BUFFER_SIZE = 4 * 1024 * 1024;

    AttributeFileBufferWriter(IAttributeFileWriter &fileWriter);
    ~AttributeFileBufferWriter() override;

    void flush() override;

    size_t getBytesWritten() const { return _bytesWritten; }
};


} // namespace search
