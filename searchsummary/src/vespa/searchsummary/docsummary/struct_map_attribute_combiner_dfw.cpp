// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_field_writer.h"
#include "docsum_field_writer_state.h"
#include "docsumstate.h"
#include "struct_fields_resolver.h"
#include "struct_map_attribute_combiner_dfw.h"
#include "summary_elements_selector.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/util/stash.h>
#include <algorithm>
#include <cassert>

using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using vespalib::slime::Cursor;

namespace search::docsummary {

namespace {

vespalib::Memory keyName("key");
vespalib::Memory valueName("value");

class StructMapAttributeFieldWriterState : public DocsumFieldWriterState
{
    // AttributeFieldWriter instance is owned by stash passed to constructor
    AttributeFieldWriter*              _keyWriter;
    // AttributeFieldWriter instances are owned by stash passed to constructor
    std::vector<AttributeFieldWriter*> _valueWriters;
    GetDocsumsState&                   _state;
    const SummaryElementsSelector&     _elements_selector;

public:
    StructMapAttributeFieldWriterState(const std::string &keyAttributeName,
                                       const std::vector<std::string> &valueFieldNames,
                                       const std::vector<std::string> &valueAttributeNames,
                                       IAttributeContext &context,
                                       GetDocsumsState& state,
                                       const SummaryElementsSelector& elements_selector);
    ~StructMapAttributeFieldWriterState() override;
    void insert_element(uint32_t element_index, Cursor &array);
    void insertField(uint32_t docId, vespalib::slime::Inserter &target) override;
};

StructMapAttributeFieldWriterState::StructMapAttributeFieldWriterState(const std::string &keyAttributeName,
                                                                       const std::vector<std::string> &valueFieldNames,
                                                                       const std::vector<std::string> &valueAttributeNames,
                                                                       IAttributeContext &context,
                                                                       GetDocsumsState& state,
                                                                       const SummaryElementsSelector& elements_selector)
    : DocsumFieldWriterState(),
      _keyWriter(nullptr),
      _valueWriters(),
      _state(state),
      _elements_selector(elements_selector)
{
    auto& stash = state.get_stash();
    const IAttributeVector *attr = context.getAttribute(keyAttributeName);
    if (attr != nullptr) {
        _keyWriter = &AttributeFieldWriter::create(keyName, *attr, stash, true);
    }
    size_t fields = valueFieldNames.size();
    _valueWriters.reserve(fields);
    for (uint32_t field = 0; field < fields; ++field) {
        attr = context.getAttribute(valueAttributeNames[field]);
        if (attr != nullptr) {
            _valueWriters.emplace_back(&AttributeFieldWriter::create(valueFieldNames[field], *attr, stash));
        }
    }
}

StructMapAttributeFieldWriterState::~StructMapAttributeFieldWriterState() = default;

void
StructMapAttributeFieldWriterState::insert_element(uint32_t element_index, Cursor &array)
{
    Cursor &keyValueObj = array.addObject();
    if (_keyWriter) {
        _keyWriter->print(element_index, keyValueObj);
    }
    Cursor &obj = keyValueObj.setObject(valueName);
    for (auto &valueWriter : _valueWriters) {
        valueWriter->print(element_index, obj);
    }
}

void
StructMapAttributeFieldWriterState::insertField(uint32_t docId, vespalib::slime::Inserter &target)
{
    uint32_t elems = 0;
    if (_keyWriter) {
        elems = _keyWriter->fetch(docId);
    }
    for (auto &valueWriter : _valueWriters) {
        elems = std::max(elems, valueWriter->fetch(docId));
    }
    if (elems == 0) {
        return;
    }
    auto elements = _elements_selector.get_selected_elements(docId, _state);
    if (!elements.all_elements()) {
        if (elements.empty() || elements.back() >= elems) {
            return;
        }
        Cursor &arr = target.insertArray();
        auto elements_iterator = elements.begin();
        for (uint32_t idx = 0; idx < elems && elements_iterator != elements.end(); ++idx) {
            assert(*elements_iterator >= idx);
            if (*elements_iterator == idx) {
                insert_element(idx, arr);
                ++elements_iterator;
            }
        }
    } else {
        Cursor &arr = target.insertArray();
        for (uint32_t idx = 0; idx < elems; ++idx) {
            insert_element(idx, arr);
        }
    }
}

}

StructMapAttributeCombinerDFW::StructMapAttributeCombinerDFW(const StructFieldsResolver& fields_resolver)
    : AttributeCombinerDFW(),
      _keyAttributeName(fields_resolver.get_map_key_attribute()),
      _valueFields(fields_resolver.get_map_value_fields()),
      _valueAttributeNames(fields_resolver.get_map_value_attributes())
{
}

StructMapAttributeCombinerDFW::~StructMapAttributeCombinerDFW() = default;

DocsumFieldWriterState*
StructMapAttributeCombinerDFW::allocFieldWriterState(IAttributeContext& context, GetDocsumsState& state,
                                                     const SummaryElementsSelector& elements_selector) const
{
    auto& stash = state.get_stash();
    return &stash.create<StructMapAttributeFieldWriterState>(_keyAttributeName, _valueFields, _valueAttributeNames, context, state, elements_selector);
}

}
