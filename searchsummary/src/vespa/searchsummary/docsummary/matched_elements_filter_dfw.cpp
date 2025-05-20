// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matched_elements_filter_dfw.h"
#include "docsumstate.h"
#include "i_docsum_store_document.h"
#include "slime_filler.h"
#include "struct_fields_resolver.h"
#include "summary_elements_selector.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/literalfieldvalue.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <cassert>

using document::FieldValue;
using document::LiteralFieldValueB;
using vespalib::Slime;
using vespalib::slime::ArrayInserter;
using vespalib::slime::BinaryFormat;
using vespalib::slime::Inserter;
using vespalib::slime::Inspector;
using vespalib::slime::SlimeInserter;
using vespalib::slime::inject;

namespace search::docsummary {

MatchedElementsFilterDFW::MatchedElementsFilterDFW(const std::string& input_field_name)
    : _input_field_name(input_field_name)
{
}

std::unique_ptr<DocsumFieldWriter>
MatchedElementsFilterDFW::create(const std::string& input_field_name)
{
    return std::make_unique<MatchedElementsFilterDFW>(input_field_name);
}

MatchedElementsFilterDFW::~MatchedElementsFilterDFW() = default;

void
MatchedElementsFilterDFW::insert_field(uint32_t, const IDocsumStoreDocument* doc, GetDocsumsState&,
                                       ElementIds selected_elements,
                                       vespalib::slime::Inserter& target) const
{
    if (doc != nullptr) {
        doc->insert_summary_field(_input_field_name, selected_elements, target);
    }
}

}
