// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumstate.h"
#include "matched_elements_filter_dfw.h"
#include "struct_fields_resolver.h"
#include "summaryfieldconverter.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/literalfieldvalue.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/struct_field_mapper.h>
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

MatchedElementsFilterDFW::MatchedElementsFilterDFW(const std::string& input_field_name, uint32_t input_field_enum,
                                                   std::shared_ptr<StructFieldMapper> struct_field_mapper)
    : _input_field_name(input_field_name),
      _input_field_enum(input_field_enum),
      _struct_field_mapper(std::move(struct_field_mapper))
{
}

std::unique_ptr<IDocsumFieldWriter>
MatchedElementsFilterDFW::create(const std::string& input_field_name, uint32_t input_field_enum,
                                 std::shared_ptr<StructFieldMapper> struct_field_mapper)
{
    return std::make_unique<MatchedElementsFilterDFW>(input_field_name, input_field_enum, std::move(struct_field_mapper));
}

std::unique_ptr<IDocsumFieldWriter>
MatchedElementsFilterDFW::create(const std::string& input_field_name, uint32_t input_field_enum,
                                 search::attribute::IAttributeContext& attr_ctx,
                                 std::shared_ptr<StructFieldMapper> struct_field_mapper)
{
    StructFieldsResolver resolver(input_field_name, attr_ctx, false);
    if (resolver.has_error()) {
        return std::unique_ptr<IDocsumFieldWriter>();
    }
    resolver.apply_to(*struct_field_mapper);
    return std::make_unique<MatchedElementsFilterDFW>(input_field_name, input_field_enum, std::move(struct_field_mapper));
}

MatchedElementsFilterDFW::~MatchedElementsFilterDFW() = default;

namespace {

void
decode_input_field_to_slime(const ResEntry& entry, search::RawBuf& target_buf, Slime& input_field_as_slime)
{
    const char* buf;
    uint32_t buf_len;
    entry._resolve_field(&buf, &buf_len, &target_buf);
    BinaryFormat::decode(vespalib::Memory(buf, buf_len), input_field_as_slime);
}

void
convert_input_field_to_slime(const FieldValue& input_field_value, Slime& input_field_as_slime)
{
    // This is the same conversion that happens in proton::DocumentStoreAdapter.
    auto converted = SummaryFieldConverter::convertSummaryField(false, input_field_value);
    // This should hold as we also have asserted that (type == ResType::RES_JSONSTRING);
    assert(converted->getClass().inherits(LiteralFieldValueB::classId));
    auto& literal = static_cast<const LiteralFieldValueB&>(*converted);
    vespalib::stringref buf = literal.getValueRef();
    BinaryFormat::decode(vespalib::Memory(buf.data(), buf.size()), input_field_as_slime);
}

bool
resolve_input_field_as_slime(GeneralResult& result, GetDocsumsState& state,
                             int entry_idx, const vespalib::string& input_field_name,
                             Slime& input_field_as_slime)
{
    ResEntry* entry = result.GetEntry(entry_idx);
    if (entry != nullptr) {
        decode_input_field_to_slime(*entry, state._docSumFieldSpace, input_field_as_slime);
        return true;
    } else {
        // Use the document instance if the input field is not in the docsum blob.
        auto field_value = result.get_field_value(input_field_name);
        if (field_value) {
            convert_input_field_to_slime(*field_value, input_field_as_slime);
            return true;
        }
    }
    return false;
}

void
filter_matching_elements_in_input_field(const Slime& input_field, const std::vector<uint32_t>& matching_elems, Slime& output_field)
{
    SlimeInserter output_inserter(output_field);
    Inspector& input_inspector = input_field.get();
    ArrayInserter array_inserter(output_inserter.insertArray());
    auto elems_itr = matching_elems.begin();
    for (size_t i = 0; (i < input_inspector.entries()) && (elems_itr != matching_elems.end()); ++i) {
        assert(*elems_itr >= i);
        if (*elems_itr == i) {
            inject(input_inspector[i], array_inserter);
            ++elems_itr;
        }
    }
}

}

void
MatchedElementsFilterDFW::insertField(uint32_t docid, GeneralResult* result, GetDocsumsState *state,
                                      ResType type, vespalib::slime::Inserter& target)
{
    assert(type == ResType::RES_JSONSTRING);
    int entry_idx = result->GetClass()->GetIndexFromEnumValue(_input_field_enum);
    Slime input_field;
    if (resolve_input_field_as_slime(*result, *state, entry_idx, _input_field_name, input_field)) {
        Slime output_field;
        filter_matching_elements_in_input_field(input_field,
                                                state->get_matching_elements(*_struct_field_mapper)
                                                        .get_matching_elements(docid, _input_field_name), output_field);
        inject(output_field.get(), target);
    }
}

}
