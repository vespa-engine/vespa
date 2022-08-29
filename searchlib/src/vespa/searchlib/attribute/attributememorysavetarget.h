// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributememoryfilewriter.h"
#include "iattributesavetarget.h"
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <memory>
#include <unordered_map>

namespace search::common { class FileHeaderContext; }

namespace search {
class AttributeVector;

/**
 * Class used to save an attribute vector to memory buffer(s).
 **/
class AttributeMemorySaveTarget : public IAttributeSaveTarget {
private:
    using FileWriterUP = std::unique_ptr<AttributeMemoryFileWriter>;
    struct WriterEntry {
        FileWriterUP writer;
        vespalib::string desc;
        WriterEntry(FileWriterUP writer_in, const vespalib::string& desc_in)
            : writer(std::move(writer_in)), desc(desc_in) {}
    };
    using WriterMap = std::unordered_map<vespalib::string, WriterEntry, vespalib::hash<vespalib::string>>;

    AttributeMemoryFileWriter _datWriter;
    AttributeMemoryFileWriter _idxWriter;
    AttributeMemoryFileWriter _weightWriter;
    AttributeMemoryFileWriter _udatWriter;
    WriterMap _writers;

public:
    AttributeMemorySaveTarget();
    ~AttributeMemorySaveTarget() override;

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

    bool setup_writer(const vespalib::string& file_suffix,
                      const vespalib::string& desc) override;
    IAttributeFileWriter& get_writer(const vespalib::string& file_suffix) override;

};

} // namespace search

