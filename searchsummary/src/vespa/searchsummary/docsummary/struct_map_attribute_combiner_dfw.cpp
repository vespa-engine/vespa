// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_field_writer.h"
#include "docsum_field_writer_state.h"
#include "struct_fields_resolver.h"
#include "struct_map_attribute_combiner_dfw.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
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
    const vespalib::string&            _field_name;
    const MatchingElements* const      _matching_elements;

public:
    StructMapAttributeFieldWriterState(const vespalib::string &keyAttributeName,
                                       const std::vector<vespalib::string> &valueFieldNames,
                                       const std::vector<vespalib::string> &valueAttributeNames,
                                       IAttributeContext &context,
                                       vespalib::Stash& stash,
                                       const vespalib::string &field_name,
                                       const MatchingElements* matching_elements);
    ~StructMapAttributeFieldWriterState() override;
    void insert_element(uint32_t element_index, Cursor &array);
    void insertField(uint32_t docId, vespalib::slime::Inserter &target) override;
};

StructMapAttributeFieldWriterState::StructMapAttributeFieldWriterState(const vespalib::string &keyAttributeName,
                                                                       const std::vector<vespalib::string> &valueFieldNames,
                                                                       const std::vector<vespalib::string> &valueAttributeNames,
                                                                       IAttributeContext &context,
                                                                       vespalib::Stash& stash,
                                                                       const vespalib::string& field_name,
                                                                       const MatchingElements *matching_elements)
    : DocsumFieldWriterState(),
      _keyWriter(nullptr),
      _valueWriters(),
      _field_name(field_name),
      _matching_elements(matching_elements)
{
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
    if (_matching_elements != nullptr) {
        auto &elements = _matching_elements->get_matching_elements(docId, _field_name);
        if (elements.empty() || elements.back() >= elems) {
            return;
        }
        Cursor &arr = target.insertArray();
        auto elements_iterator = elements.cbegin();
        for (uint32_t idx = 0; idx < elems && elements_iterator != elements.cend(); ++idx) {
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

StructMapAttributeCombinerDFW::StructMapAttributeCombinerDFW(const vespalib::string &fieldName,
                                                             const StructFieldsResolver& fields_resolver,
                                                             bool filter_elements,
                                                             std::shared_ptr<MatchingElementsFields> matching_elems_fields)
    : AttributeCombinerDFW(fieldName, filter_elements, std::move(matching_elems_fields)),
      _keyAttributeName(fields_resolver.get_map_key_attribute()),
      _valueFields(fields_resolver.get_map_value_fields()),
      _valueAttributeNames(fields_resolver.get_map_value_attributes())
{
    if (filter_elements && _matching_elems_fields && !_matching_elems_fields->has_field(fieldName)) {
        fields_resolver.apply_to(*_matching_elems_fields);
    }
}

StructMapAttributeCombinerDFW::~StructMapAttributeCombinerDFW() = default;

DocsumFieldWriterState*
StructMapAttributeCombinerDFW::allocFieldWriterState(IAttributeContext &context, vespalib::Stash& stash, const MatchingElements* matching_elements) const
{
    return &stash.create<StructMapAttributeFieldWriterState>(_keyAttributeName, _valueFields, _valueAttributeNames, context, stash, _fieldName, matching_elements);
}

}
