// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/iblobconverter.h>
#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace common {

class PassThroughConverter : public BlobConverter
{
private:
    virtual ConstBufferRef onConvert(const ConstBufferRef & src) const;
};

class LowercaseConverter : public BlobConverter
{
public:
    LowercaseConverter();
private:
    virtual ConstBufferRef onConvert(const ConstBufferRef & src) const;
    mutable vespalib::string _buffer;
};

class ConverterFactory {
protected:
    using stringref = vespalib::stringref;
public:
    virtual ~ConverterFactory() { }
    virtual BlobConverter::UP create(stringref local, stringref strength) const = 0;
};

}
}

