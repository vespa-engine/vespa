// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributesavetarget.h"
#include <vespa/searchlib/util/rawbuf.h>
#include <memory>
#include <vespa/searchlib/common/tunefileinfo.h>
#include "attributememoryfilewriter.h"

namespace search
{

namespace common
{

class FileHeaderContext;

}

class AttributeVector;

/**
 * Class used to save an attribute vector to memory buffer(s).
 **/
class AttributeMemorySaveTarget : public IAttributeSaveTarget
{
private:
    AttributeMemoryFileWriter _datWriter;
    AttributeMemoryFileWriter _idxWriter;
    AttributeMemoryFileWriter _weightWriter;
    AttributeMemoryFileWriter _udatWriter;

public:
    AttributeMemorySaveTarget();
    ~AttributeMemorySaveTarget();

    /**
     * Write the underlying buffer(s) to file(s).
     **/
    bool
    writeToFile(const TuneFileAttributes &tuneFileAttributes,
                const search::common::FileHeaderContext &fileHeaderContext);

    // Implements IAttributeSaveTarget
    virtual bool setup() override { return true; }
    virtual void close() override {}
    virtual IAttributeFileWriter &datWriter() override;
    virtual IAttributeFileWriter &idxWriter() override;
    virtual IAttributeFileWriter &weightWriter() override;
    virtual IAttributeFileWriter &udatWriter() override;
};

} // namespace search

