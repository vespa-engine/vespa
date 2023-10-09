// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributesavetarget.h"
#include "attributefilewriter.h"
#include <vespa/vespalib/stllike/hash_fun.h>
#include <unordered_map>

namespace search {

/**
 * Class used to save an attribute vector to file(s).
 **/
class AttributeFileSaveTarget : public IAttributeSaveTarget {
private:
    using FileWriterUP = std::unique_ptr<AttributeFileWriter>;
    using WriterMap = std::unordered_map<vespalib::string, FileWriterUP, vespalib::hash<vespalib::string>>;

    const TuneFileAttributes& _tune_file;
    const search::common::FileHeaderContext& _file_header_ctx;
    AttributeFileWriter _datWriter;
    AttributeFileWriter _idxWriter;
    AttributeFileWriter _weightWriter;
    AttributeFileWriter _udatWriter;
    WriterMap _writers;

public:
    AttributeFileSaveTarget(const TuneFileAttributes& tune_file,
                            const search::common::FileHeaderContext& file_header_ctx);
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

    bool setup_writer(const vespalib::string& file_suffix,
                      const vespalib::string& desc) override;
    IAttributeFileWriter& get_writer(const vespalib::string& file_suffix) override;

};

} // namespace search

