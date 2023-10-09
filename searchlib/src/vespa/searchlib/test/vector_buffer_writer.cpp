// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vector_buffer_writer.h"

namespace search::test {

VectorBufferWriter::VectorBufferWriter()
    : BufferWriter()
{
    setup(tmp, 1024);
}

VectorBufferWriter::~VectorBufferWriter() = default;

void
VectorBufferWriter::flush()
{
    for (size_t i = 0; i < usedLen(); ++i) {
        output.push_back(tmp[i]);
    }
    rewind();
}

}
