// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefilebufferwriter.h"

#include <vespa/check_require.h>
#include <vespa/vespalib/data/databuffer.h>

namespace search {

AttributeFileBufferWriter::AttributeFileBufferWriter(IAttributeFileWriter& fileWriter)
    : BufferWriter(), _buf(), _bytesWritten(0), _incompleteBuffers(0), _fileWriter(fileWriter) {
    _buf = _fileWriter.allocBuf(BUFFER_SIZE);
    CHECK(_buf->getFreeLen() >= BUFFER_SIZE);
    setup(_buf->getFree(), BUFFER_SIZE);
}

AttributeFileBufferWriter::~AttributeFileBufferWriter() {
    CHECK(usedLen() == 0);
}

void AttributeFileBufferWriter::flush() {
    CHECK(_incompleteBuffers == 0); // all previous buffers must have been full
    size_t nowLen = usedLen();
    if (nowLen != BUFFER_SIZE) {
        // buffer is not full, only allowed for last buffer
        ++_incompleteBuffers;
    }
    if (nowLen == 0) {
        return; // empty buffer
    }
    CHECK(_buf->getDataLen() == 0);
    onFlush(nowLen);
    CHECK(_buf->getFreeLen() >= BUFFER_SIZE);
    setup(_buf->getFree(), BUFFER_SIZE);
    _bytesWritten += nowLen;
}

} // namespace search
