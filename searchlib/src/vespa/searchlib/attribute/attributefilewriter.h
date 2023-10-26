// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributefilewriter.h"
#include <vespa/vespalib/stllike/string.h>

class FastOS_FileInterface;

namespace vespalib { class GenericHeader; }

namespace search {

namespace common { class FileHeaderContext; }
namespace attribute { class AttributeHeader; }

class TuneFileAttributes;

/*
 * Class to write to a single attribute vector file. Used by
 * AttributeFileSaveTarget.
 */
class AttributeFileWriter : public IAttributeFileWriter
{
    std::unique_ptr<FastOS_FileInterface> _file;
    const TuneFileAttributes &_tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    const attribute::AttributeHeader &_header;
    vespalib::string _desc;
    uint64_t _fileBitSize;

    void addTags(vespalib::GenericHeader &header);

    void writeHeader();
public:
    AttributeFileWriter(const TuneFileAttributes &tuneFileAttributes,
                        const search::common::FileHeaderContext & fileHeaderContext,
                        const attribute::AttributeHeader &header,
                        const vespalib::string &desc);
    ~AttributeFileWriter();
    Buffer allocBuf(size_t size) override;
    void writeBuf(Buffer buf) override;
    std::unique_ptr<BufferWriter> allocBufferWriter() override;
    bool open(const vespalib::string &fileName);
    void close();
};


} // namespace search
