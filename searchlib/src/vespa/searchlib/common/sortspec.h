// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "converters.h"
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/util/buffer.h>
#include <string>
#include <vector>

namespace search::common {

struct SortInfo {
    SortInfo(std::string_view field, bool ascending, BlobConverter::SP converter) noexcept;
    ~SortInfo();
    std::string      _field;
    bool                  _ascending;
    BlobConverter::SP     _converter;
};

class SortSpec : public std::vector<SortInfo>
{
public:
    SortSpec() : _spec() { }
    SortSpec(const std::string & spec, const ConverterFactory & ucaFactory);
    ~SortSpec();
    const std::string & getSpec() const { return _spec; }
private:
    std::string _spec;
};

}
