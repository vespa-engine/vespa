// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/bufferwriter.h>
#include <vector>

namespace search::test
{

/*
 * Class used by hnsw graph/index unit tests to save hnsw index to a
 * vector.
 */
class VectorBufferWriter : public BufferWriter {
private:
    char tmp[1024];
public:
    std::vector<char> output;
    VectorBufferWriter();
    ~VectorBufferWriter() override;
    void flush() override;
};

}
