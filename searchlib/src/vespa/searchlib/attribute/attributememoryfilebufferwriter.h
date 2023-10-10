// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributefilebufferwriter.h"

namespace search
{

/*
 * BufferWriter implementation that passes full buffers on to
 * memory variant of IAttributeFileWriter.
 */
class AttributeMemoryFileBufferWriter : public AttributeFileBufferWriter
{
public:
    AttributeMemoryFileBufferWriter(IAttributeFileWriter &memoryFileWriter);

    virtual ~AttributeMemoryFileBufferWriter();

    virtual void onFlush(size_t nowSize) override;
};


} // namespace search
