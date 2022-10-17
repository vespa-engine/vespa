// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "converters.h"
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search::common {

struct SortInfo {
    SortInfo(vespalib::stringref field, bool ascending, BlobConverter::SP converter) noexcept;
    ~SortInfo();
    vespalib::string      _field;
    bool                  _ascending;
    BlobConverter::SP     _converter;
};

class SortSpec : public std::vector<SortInfo>
{
public:
    SortSpec() : _spec() { }
    SortSpec(const vespalib::string & spec, const ConverterFactory & ucaFactory);
    ~SortSpec();
    const vespalib::string & getSpec() const { return _spec; }
private:
    vespalib::string _spec;
};

}
