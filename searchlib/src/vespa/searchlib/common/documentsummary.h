// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::docsummary {

class DocumentSummary
{
public:
    static bool readDocIdLimit(const vespalib::string &dir, uint32_t &docIdLimit);
    static bool writeDocIdLimit(const vespalib::string &dir, uint32_t docIdLimit);
};

}

