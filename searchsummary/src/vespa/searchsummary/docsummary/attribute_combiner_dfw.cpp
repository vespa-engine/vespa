// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_attribute_combiner_dfw.h"
#include "attribute_combiner_dfw.h"
#include "docsum_field_writer_state.h"
#include "docsumstate.h"
#include "struct_fields_resolver.h"
#include "struct_map_attribute_combiner_dfw.h"
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.attribute_combiner_dfw");

using search::attribute::IAttributeContext;

namespace search::docsummary {

AttributeCombinerDFW::AttributeCombinerDFW(const std::string &fieldName)
    : DocsumFieldWriter(),
      _stateIndex(0),
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
AttributeCombinerDFW::create(const std::string &fieldName, IAttributeContext &attrCtx)
{
    StructFieldsResolver structFields(fieldName, attrCtx, true);
    if (structFields.has_error()) {
        return {};
    } else if (structFields.is_map_of_struct()) {
        return std::make_unique<StructMapAttributeCombinerDFW>(fieldName, structFields);
    }
    return std::make_unique<ArrayAttributeCombinerDFW>(fieldName, structFields);
}

void
AttributeCombinerDFW::insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                                   const SummaryElementsSelector& elements_selector,
                                   vespalib::slime::Inserter& target) const
{
    (void) doc;
    (void) elements_selector;
    auto& fieldWriterState = state._fieldWriterStates[_stateIndex];
    if (!fieldWriterState) {
        fieldWriterState = allocFieldWriterState(*state._attrCtx, state, elements_selector);
    }
    fieldWriterState->insertField(docid, target);
}

}

