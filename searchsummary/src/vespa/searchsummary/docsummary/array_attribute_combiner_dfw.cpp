// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_attribute_combiner_dfw.h"
#include "attribute_field_writer.h"
#include "docsum_field_writer_state.h"
#include "struct_fields_resolver.h"
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

class ArrayAttributeFieldWriterState : public DocsumFieldWriterState
{
    // AttributeFieldWriter instances are owned by stash passed to constructor
    std::vector<AttributeFieldWriter*>                 _writers;
    const vespalib::string&                            _field_name;
    const MatchingElements* const                      _matching_elements;

public:
    ArrayAttributeFieldWriterState(const std::vector<vespalib::string> &fieldNames,
                                   const std::vector<vespalib::string> &attributeNames,
                                   IAttributeContext &context,
                                   vespalib::Stash& stash,
                                   const vespalib::string &field_name,
                                   const MatchingElements* matching_elements,
                                   bool is_map_of_scalar);
    ~ArrayAttributeFieldWriterState() override;
    void insert_element(uint32_t element_index, Cursor &array);
    void insertField(uint32_t docId, vespalib::slime::Inserter &target) override;
};

ArrayAttributeFieldWriterState::ArrayAttributeFieldWriterState(const std::vector<vespalib::string> &fieldNames,
                                                               const std::vector<vespalib::string> &attributeNames,
                                                               IAttributeContext &context,
                                                               vespalib::Stash& stash,
                                                               const vespalib::string &field_name,
                                                               const MatchingElements *matching_elements,
                                                               bool is_map_of_scalar)
    : DocsumFieldWriterState(),
      _writers(),
      _field_name(field_name),
      _matching_elements(matching_elements)
{
    size_t fields = fieldNames.size();
    _writers.reserve(fields);
    for (uint32_t field = 0; field < fields; ++field) {
        const IAttributeVector *attr = context.getAttribute(attributeNames[field]);
        if (attr != nullptr) {
            _writers.emplace_back(&AttributeFieldWriter::create(fieldNames[field], *attr, stash, is_map_of_scalar));
        }
    }
}

ArrayAttributeFieldWriterState::~ArrayAttributeFieldWriterState() = default;

void
ArrayAttributeFieldWriterState::insert_element(uint32_t element_index, Cursor &array)
{
    Cursor &obj = array.addObject();
    for (auto &writer : _writers) {
        writer->print(element_index, obj);
    }
}

void
ArrayAttributeFieldWriterState::insertField(uint32_t docId, vespalib::slime::Inserter &target)
{
    uint32_t elems = 0;
    for (auto &writer : _writers) {
        elems = std::max(elems, writer->fetch(docId));
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

ArrayAttributeCombinerDFW::ArrayAttributeCombinerDFW(const vespalib::string &fieldName,
                                                     const StructFieldsResolver& fields_resolver,
                                                     bool filter_elements,
                                                     std::shared_ptr<MatchingElementsFields> matching_elems_fields)
    : AttributeCombinerDFW(fieldName, filter_elements, std::move(matching_elems_fields)),
      _fields(fields_resolver.get_array_fields()),
      _attributeNames(fields_resolver.get_array_attributes()),
      _is_map_of_scalar(fields_resolver.is_map_of_scalar())
{
    if (filter_elements && _matching_elems_fields && !_matching_elems_fields->has_field(fieldName)) {
        fields_resolver.apply_to(*_matching_elems_fields);
    }
}

ArrayAttributeCombinerDFW::~ArrayAttributeCombinerDFW() = default;

DocsumFieldWriterState*
ArrayAttributeCombinerDFW::allocFieldWriterState(IAttributeContext &context, vespalib::Stash &stash, const MatchingElements* matching_elements) const
{
    return &stash.create<ArrayAttributeFieldWriterState>(_fields, _attributeNames, context, stash, _fieldName, matching_elements, _is_map_of_scalar);
}

}
