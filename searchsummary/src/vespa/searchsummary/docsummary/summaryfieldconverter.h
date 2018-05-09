// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>

namespace search::docsummary {

/**
 * This class converts a summary field for docsum fetching.
 */
class SummaryFieldConverter
{
public:
    static document::FieldValue::UP
    convertSummaryField(bool markup, const document::FieldValue &value);
};

}

