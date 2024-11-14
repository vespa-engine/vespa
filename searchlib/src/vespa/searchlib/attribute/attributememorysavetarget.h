// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        std::string desc;
        WriterEntry(FileWriterUP writer_in, std::string desc_in)
            : writer(std::move(writer_in)),
              desc(std::move(desc_in))
        {}
        WriterEntry(WriterEntry &&) noexcept = default;
        WriterEntry & operator=(WriterEntry &&) noexcept = default;
        ~WriterEntry();
    };
    using WriterMap = std::unordered_map<std::string, WriterEntry, vespalib::hash<std::string>>;

    AttributeMemoryFileWriter _datWriter;
    AttributeMemoryFileWriter _idxWriter;
    AttributeMemoryFileWriter _weightWriter;
    AttributeMemoryFileWriter _udatWriter;
    WriterMap                 _writers;
    uint64_t                  _size_on_disk;

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

    bool setup_writer(const std::string& file_suffix,
                      const std::string& desc) override;
    IAttributeFileWriter& get_writer(const std::string& file_suffix) override;
    uint64_t size_on_disk() const noexcept override;
};

} // namespace search

