// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matched_elements_filter_dfw.h"
#include "docsumstate.h"
#include "i_docsum_store_document.h"
#include "slime_filler.h"
#include "struct_fields_resolver.h"
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

const std::vector<uint32_t>&
MatchedElementsFilterDFW::get_matching_elements(uint32_t docid, GetDocsumsState& state) const
{
    return state.get_matching_elements(*_matching_elems_fields).get_matching_elements(docid, _input_field_name);
}

MatchedElementsFilterDFW::MatchedElementsFilterDFW(const std::string& input_field_name,
                                                   std::shared_ptr<MatchingElementsFields> matching_elems_fields)
    : _input_field_name(input_field_name),
      _matching_elems_fields(std::move(matching_elems_fields))
{
}

std::unique_ptr<DocsumFieldWriter>
MatchedElementsFilterDFW::create(const std::string& input_field_name,
                                 std::shared_ptr<MatchingElementsFields> matching_elems_fields)
{
    return std::make_unique<MatchedElementsFilterDFW>(input_field_name, std::move(matching_elems_fields));
}

std::unique_ptr<DocsumFieldWriter>
MatchedElementsFilterDFW::create(const std::string& input_field_name,
                                 search::attribute::IAttributeContext& attr_ctx,
                                 std::shared_ptr<MatchingElementsFields> matching_elems_fields)
{
    StructFieldsResolver resolver(input_field_name, attr_ctx, false);
    if (resolver.has_error()) {
        return {};
    }
    resolver.apply_to(*matching_elems_fields);
    return std::make_unique<MatchedElementsFilterDFW>(input_field_name, std::move(matching_elems_fields));
}

MatchedElementsFilterDFW::~MatchedElementsFilterDFW() = default;

void
MatchedElementsFilterDFW::insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                                      vespalib::slime::Inserter& target) const
{
    auto field_value = doc->get_field_value(_input_field_name);
    if (field_value) {
        SlimeFiller::insert_summary_field_with_filter(*field_value, target, get_matching_elements(docid, state));
    }
}

}
