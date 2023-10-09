// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "compressor.h"

namespace vespalib::compression {

class LZ4Compressor : public ICompressor
{
public:
    bool process(CompressionConfig config, const void * input, size_t inputLen, void * output, size_t & outputLen) override;
    bool unprocess(const void * input, size_t inputLen, void * output, size_t & outputLen) override;
    size_t adjustProcessLen(uint16_t options, size_t len)   const override;
};

}

