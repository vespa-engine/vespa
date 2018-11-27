// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributesavetarget.h"
#include "attributememoryfilewriter.h"
#include <vespa/searchlib/util/rawbuf.h>
#include <memory>
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search::common { class FileHeaderContext; }

namespace search {
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
    bool writeToFile(const TuneFileAttributes &tuneFileAttributes,
                     const common::FileHeaderContext &fileHeaderContext);

    bool setup() override { return true; }
    void close() override {}
    IAttributeFileWriter &datWriter() override;
    IAttributeFileWriter &idxWriter() override;
    IAttributeFileWriter &weightWriter() override;
    IAttributeFileWriter &udatWriter() override;
};

} // namespace search

