// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::common {

class PassThroughConverter : public BlobConverter
{
private:
    ConstBufferRef onConvert(const ConstBufferRef & src) const override;
};

class LowercaseConverter : public BlobConverter
{
public:
    LowercaseConverter() noexcept;
private:
    ConstBufferRef onConvert(const ConstBufferRef & src) const override;
    mutable vespalib::string _buffer;
};

class ConverterFactory {
protected:
    using stringref = vespalib::stringref;
public:
    virtual ~ConverterFactory() = default;
    virtual BlobConverter::UP create(stringref local, stringref strength) const = 0;
};

}
