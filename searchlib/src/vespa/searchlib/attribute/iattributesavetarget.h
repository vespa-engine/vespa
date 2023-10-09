// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributefilewriter.h"
#include "attribute_header.h"

namespace search {

/**
 * Interface used for saving an attribute vector.
 **/
class IAttributeSaveTarget {
public:
    using Buffer = IAttributeFileWriter::Buffer;
protected:
    attribute::AttributeHeader _header;
public:
    IAttributeSaveTarget() : _header() {}
    void setHeader(const attribute::AttributeHeader & header) { _header = header; }
    const attribute::AttributeHeader & getHeader() const { return _header; }

    bool getEnumerated() const { return _header.getEnumerated(); }

    /**
     * Setups this saveTarget before any data is written. Returns true
     * on success.
     **/
    virtual bool setup() = 0;
    /**
     * Closes this saveTarget when all data is written.
     **/
    virtual void close() = 0;

    virtual IAttributeFileWriter &datWriter() = 0;
    virtual IAttributeFileWriter &idxWriter() = 0;
    virtual IAttributeFileWriter &weightWriter() = 0;
    virtual IAttributeFileWriter &udatWriter() = 0;

    /**
     * Setups a custom file writer with the given file suffix and description in the file header.
     * Returns false if the file writer cannot be setup or if it already exists, true otherwise.
     */
    virtual bool setup_writer(const vespalib::string& file_suffix,
                              const vespalib::string& desc) = 0;

    /**
     * Returns the file writer with the given file suffix.
     * Throws vespalib::IllegalArgumentException if the file writer does not exists.
     */
    virtual IAttributeFileWriter& get_writer(const vespalib::string& file_suffix) = 0;

    virtual ~IAttributeSaveTarget();
};

} // namespace search

