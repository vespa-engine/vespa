// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/linkedptr.h>

namespace search {
namespace common {

class BlobConverter
{
public:
    typedef std::shared_ptr<BlobConverter> SP;
    typedef vespalib::LinkedPtr<BlobConverter> LP;
    virtual ~BlobConverter() { }
    vespalib::ConstBufferRef convert(const vespalib::ConstBufferRef & src) const { return onConvert(src); }
private:
    virtual vespalib::ConstBufferRef onConvert(const vespalib::ConstBufferRef & src) const = 0;
};

}
}

