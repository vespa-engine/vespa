// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "iattributefilewriter.h"

namespace search {

/*
 * Class for saving a plain attribute (i.e. single value numeric
 * atttribute).
 */
class SingleValueNumericAttributeSaver : public AttributeSaver
{
public:
    using Buffer = IAttributeFileWriter::Buffer;

private:
    Buffer _buf;
    using BufferBuf = IAttributeFileWriter::BufferBuf;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    SingleValueNumericAttributeSaver(const attribute::AttributeHeader &header,
                                     const void *data, size_t size);

    ~SingleValueNumericAttributeSaver() override;
};

} // namespace search
