// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/buffer.h>
#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/searchlib/common/converters.h>

namespace search {
namespace common {

struct SortInfo {
    SortInfo(const vespalib::string & field, bool ascending, const BlobConverter::SP & converter) : _field(field), _ascending(ascending), _converter(converter) { }
    vespalib::string      _field;
    bool                  _ascending;
    BlobConverter::SP     _converter;
};

class SortSpec : public std::vector<SortInfo>
{
public:
    SortSpec() : _spec() { }
    SortSpec(const vespalib::string & spec, const ConverterFactory & ucaFactory);
    const vespalib::string & getSpec() const { return _spec; }
private:
    vespalib::string _spec;
};

}
}

