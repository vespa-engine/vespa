// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_attribute_combiner_dfw.h"
#include "attribute_combiner_dfw.h"
#include "docsum_field_writer_state.h"
#include "docsumstate.h"
#include "struct_fields_resolver.h"
#include "struct_map_attribute_combiner_dfw.h"
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.attribute_combiner_dfw");

using search::attribute::IAttributeContext;

namespace search::docsummary {

AttributeCombinerDFW::AttributeCombinerDFW(const std::string &fieldName, bool filter_elements)
    : SimpleDFW(),
      _stateIndex(0),
      _filter_elements(filter_elements),
      _fieldName(fieldName)
{
}

AttributeCombinerDFW::~AttributeCombinerDFW() = default;

bool
AttributeCombinerDFW::setFieldWriterStateIndex(uint32_t fieldWriterStateIndex)
{
    _stateIndex = fieldWriterStateIndex;
    return true;
}

std::unique_ptr<DocsumFieldWriter>
AttributeCombinerDFW::create(const std::string &fieldName, IAttributeContext &attrCtx,
                             const SummaryElementsSelector& elements_selector)
{
    StructFieldsResolver structFields(fieldName, attrCtx, true);
    if (structFields.has_error()) {
        return {};
    } else if (structFields.is_map_of_struct()) {
        return std::make_unique<StructMapAttributeCombinerDFW>(fieldName, structFields, elements_selector);
    }
    return std::make_unique<ArrayAttributeCombinerDFW>(fieldName, structFields, elements_selector);
}

void
AttributeCombinerDFW::insertField(uint32_t docid, GetDocsumsState& state, vespalib::slime::Inserter &target) const
{
    auto& fieldWriterState = state._fieldWriterStates[_stateIndex];
    if (!fieldWriterState) {
        const MatchingElements *matching_elements = nullptr;
        if (_filter_elements) {
            matching_elements = &state.get_matching_elements();
        }
        fieldWriterState = allocFieldWriterState(*state._attrCtx, state.get_stash(), matching_elements);
    }
    fieldWriterState->insertField(docid, target);
}

}

