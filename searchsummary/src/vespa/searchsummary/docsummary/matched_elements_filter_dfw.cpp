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
MatchedElementsFilterDFW::insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                                       const SummaryElementsSelector& elements_selector,
                                       vespalib::slime::Inserter& target) const
{
    if (doc != nullptr) {
        if (elements_selector.all_elements()) {
            doc->insert_summary_field(_input_field_name, target);
        } else {
            auto field_value = doc->get_field_value(_input_field_name);
            if (field_value) {
                auto selected_elements = elements_selector.get_selected_elements(docid, state);
                if (selected_elements != nullptr) {
                    SlimeFiller::insert_summary_field_with_filter(*field_value, target, *selected_elements);
                } else {
                    SlimeFiller::insert_summary_field(*field_value, target);
                }
            }
        }
    }
}

}
