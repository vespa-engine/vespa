// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryfieldconverter.h"
#include "check_undefined_value_visitor.h"
#include "slime_filler.h"
#include <vespa/document/fieldvalue/fieldvalue.h>

using document::FieldValue;

namespace search::docsummary {

void
SummaryFieldConverter::insert_summary_field(const FieldValue& value, vespalib::slime::Inserter& inserter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, false);
        value.accept(visitor);
    }
}

void
SummaryFieldConverter::insert_summary_field_with_filter(const FieldValue& value, vespalib::slime::Inserter& inserter, const std::vector<uint32_t>& matching_elems)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, false, &matching_elems);
        value.accept(visitor);
    }
}

void
SummaryFieldConverter::insert_juniper_field(const document::FieldValue& value, vespalib::slime::Inserter& inserter, bool tokenize, IJuniperConverter& converter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, tokenize, &converter);
        value.accept(visitor);
    }
}

}
