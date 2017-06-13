// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace docsummary {

class DocumentSummary
{
public:
    static bool
    readDocIdLimit(const vespalib::string &dir, uint32_t &docIdLimit);

    static bool
    writeDocIdLimit(const vespalib::string &dir, uint32_t docIdLimit);
};

}
}

