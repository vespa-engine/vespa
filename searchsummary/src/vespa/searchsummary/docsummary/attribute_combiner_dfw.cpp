// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_attribute_combiner_dfw.h"
#include "attribute_combiner_dfw.h"
#include "docsum_field_writer_state.h"
#include "docsumstate.h"
#include "struct_fields_resolver.h"
#include "struct_map_attribute_combiner_dfw.h"
#include <vespa/searchlib/common/struct_field_mapper.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.attribute_combiner_dfw");

using search::attribute::IAttributeContext;

namespace search::docsummary {

AttributeCombinerDFW::AttributeCombinerDFW(const vespalib::string &fieldName, bool filter_elements, std::shared_ptr<StructFieldMapper> struct_field_mapper)
    : ISimpleDFW(),
      _stateIndex(0),
      _filter_elements(filter_elements),
      _fieldName(fieldName),
      _struct_field_mapper(std::move(struct_field_mapper))
{
}

AttributeCombinerDFW::~AttributeCombinerDFW() = default;

bool
AttributeCombinerDFW::IsGenerated() const
{
    return true;
}

bool
AttributeCombinerDFW::setFieldWriterStateIndex(uint32_t fieldWriterStateIndex)
{
    _stateIndex = fieldWriterStateIndex;
    return true;
}

std::unique_ptr<IDocsumFieldWriter>
AttributeCombinerDFW::create(const vespalib::string &fieldName, IAttributeContext &attrCtx, bool filter_elements, std::shared_ptr<StructFieldMapper> struct_field_mapper)
{
    StructFieldsResolver structFields(fieldName, attrCtx, true);
    if (structFields.has_error()) {
        return std::unique_ptr<IDocsumFieldWriter>();
    } else if (structFields.is_map_of_struct()) {
        return std::make_unique<StructMapAttributeCombinerDFW>(fieldName, structFields, filter_elements, std::move(struct_field_mapper));
    }
    return std::make_unique<ArrayAttributeCombinerDFW>(fieldName, structFields, filter_elements, std::move(struct_field_mapper));
}

void
AttributeCombinerDFW::insertField(uint32_t docid, GetDocsumsState *state, ResType, vespalib::slime::Inserter &target)
{
    auto &fieldWriterState = state->_fieldWriterStates[_stateIndex];
    if (!fieldWriterState) {
        const MatchingElements *matching_elements = nullptr;
        if (_filter_elements) {
            matching_elements = &state->get_matching_elements(*_struct_field_mapper);
        }
        fieldWriterState = allocFieldWriterState(*state->_attrCtx, matching_elements);
    }
    fieldWriterState->insertField(docid, target);
}

}

