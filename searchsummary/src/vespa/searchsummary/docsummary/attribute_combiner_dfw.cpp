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

AttributeCombinerDFW::AttributeCombinerDFW()
    : DocsumFieldWriter(),
      _stateIndex(0)
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
        return std::make_unique<StructMapAttributeCombinerDFW>(structFields);
    }
    return std::make_unique<ArrayAttributeCombinerDFW>(structFields);
}

void
AttributeCombinerDFW::insert_field(uint32_t docid, const IDocsumStoreDocument*, GetDocsumsState& state,
                                   ElementIds selected_elements,
                                   vespalib::slime::Inserter& target) const
{
    auto& fieldWriterState = state._fieldWriterStates[_stateIndex];
    if (!fieldWriterState) {
        fieldWriterState = allocFieldWriterState(*state._attrCtx, state.get_stash());
    }
    fieldWriterState->insertField(docid, selected_elements, target);
}

}

