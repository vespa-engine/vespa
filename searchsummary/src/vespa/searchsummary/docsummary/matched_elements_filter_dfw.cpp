// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matched_elements_filter_dfw.h"
#include "docsumstate.h"
#include "i_docsum_store_document.h"
#include "struct_fields_resolver.h"
#include "summaryfieldconverter.h"
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
        return std::unique_ptr<DocsumFieldWriter>();
    }
    resolver.apply_to(*matching_elems_fields);
    return std::make_unique<MatchedElementsFilterDFW>(input_field_name, std::move(matching_elems_fields));
}

MatchedElementsFilterDFW::~MatchedElementsFilterDFW() = default;

namespace {

void
filter_matching_elements_in_input_field_while_converting_to_slime(const FieldValue& input_field_value,
                                                                  const std::vector<uint32_t>& matching_elems,
                                                                  vespalib::slime::Inserter& target)
{
    // This is a similar conversion that happens in proton::DocumentStoreAdapter.
    // Only difference is that we filter matched elements on the fly.
    auto converted = SummaryFieldConverter::convert_field_with_filter(false, input_field_value, matching_elems);
    // This should hold as we also have asserted that (type == ResType::RES_JSONSTRING);
    assert(converted->isLiteral());
    auto& literal = static_cast<const LiteralFieldValueB&>(*converted);
    vespalib::stringref buf = literal.getValueRef();
    if (buf.empty()) {
        return;
    }
    Slime input_field_as_slime;
    BinaryFormat::decode(vespalib::Memory(buf.data(), buf.size()), input_field_as_slime);
    inject(input_field_as_slime.get(), target);
}

}

void
MatchedElementsFilterDFW::insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState *state,
                                      ResType type, vespalib::slime::Inserter& target) const
{
    assert(type == ResType::RES_JSONSTRING);
    auto field_value = doc->get_field_value(_input_field_name);
    if (field_value) {
        filter_matching_elements_in_input_field_while_converting_to_slime(*field_value, get_matching_elements(docid, *state), target);
    }
}

}
