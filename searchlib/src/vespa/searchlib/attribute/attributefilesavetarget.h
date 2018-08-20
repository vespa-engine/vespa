// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributesavetarget.h"
#include "attributefilewriter.h"

namespace search
{

/**
 * Class used to save an attribute vector to file(s).
 **/
class AttributeFileSaveTarget : public IAttributeSaveTarget
{
private:
    AttributeFileWriter _datWriter;
    AttributeFileWriter _idxWriter;
    AttributeFileWriter _weightWriter;
    AttributeFileWriter _udatWriter;

public:
    AttributeFileSaveTarget(const TuneFileAttributes &tuneFileAttributes,
                            const search::common::FileHeaderContext &fileHeaderContext);
    ~AttributeFileSaveTarget() override;

    // Implements IAttributeSaveTarget
    /** Setups this saveTarget by opening the relevant files **/
    bool setup() override;

    /** Closes the files used **/
    void close() override;

    IAttributeFileWriter &datWriter() override;
    IAttributeFileWriter &idxWriter() override;
    IAttributeFileWriter &weightWriter() override;
    IAttributeFileWriter &udatWriter() override;
};

} // namespace search

