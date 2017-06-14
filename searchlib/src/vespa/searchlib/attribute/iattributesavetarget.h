// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/databuffer.h>
#include <stdint.h>
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

    virtual ~IAttributeSaveTarget();
};

} // namespace search

