// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_attribute_combiner_dfw.h"
#include "docsum_field_writer_state.h"
#include "attribute_field_writer.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <cassert>

using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using vespalib::slime::Cursor;

namespace search::docsummary {

namespace {

class ArrayAttributeFieldWriterState : public DocsumFieldWriterState
{
    std::vector<std::unique_ptr<AttributeFieldWriter>> _writers;
    const vespalib::string&                            _field_name;
    const MatchingElements* const                      _matching_elements;

public:
    ArrayAttributeFieldWriterState(const std::vector<vespalib::string> &fieldNames,
                                   const std::vector<vespalib::string> &attributeNames,
                                   IAttributeContext &context,
                                   const vespalib::string &field_name,
                                   const MatchingElements* matching_elements);
    ~ArrayAttributeFieldWriterState() override;
    void insert_element(uint32_t element_index, Cursor &array);
    void insertField(uint32_t docId, vespalib::slime::Inserter &target) override;
};

ArrayAttributeFieldWriterState::ArrayAttributeFieldWriterState(const std::vector<vespalib::string> &fieldNames,
                                                               const std::vector<vespalib::string> &attributeNames,
                                                               IAttributeContext &context,
                                                               const vespalib::string &field_name,
                                                               const MatchingElements *matching_elements)
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
            _writers.emplace_back(AttributeFieldWriter::create(fieldNames[field], *attr));
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
        writer->fetch(docId);
        if (elems < writer->size()) {
            elems = writer->size();
        }
    }
    if (elems == 0) {
        return;
    }
    Cursor &arr = target.insertArray();
    if (_matching_elements != nullptr) {
        auto &elements = _matching_elements->get_matching_elements(docId, _field_name);
        auto elements_iterator = elements.cbegin();
        for (uint32_t idx = 0; idx < elems && elements_iterator != elements.cend(); ++idx) {
            assert(*elements_iterator >= idx);
            if (*elements_iterator == idx) {
                insert_element(idx, arr);
                ++elements_iterator;
            }
        }
    } else {
        for (uint32_t idx = 0; idx < elems; ++idx) {
            insert_element(idx, arr);
        }
    }
}

}

ArrayAttributeCombinerDFW::ArrayAttributeCombinerDFW(const vespalib::string &fieldName,
                                                     const std::vector<vespalib::string> &fields,
                                                     bool filter_elements)
    : AttributeCombinerDFW(fieldName, filter_elements),
      _fields(fields),
      _attributeNames()
{
    _attributeNames.reserve(_fields.size());
    vespalib::string prefix = fieldName + ".";
    for (const auto &field : _fields) {
        _attributeNames.emplace_back(prefix + field);
    }
}

ArrayAttributeCombinerDFW::~ArrayAttributeCombinerDFW() = default;

std::unique_ptr<DocsumFieldWriterState>
ArrayAttributeCombinerDFW::allocFieldWriterState(IAttributeContext &context, const MatchingElements* matching_elements)
{
    return std::make_unique<ArrayAttributeFieldWriterState>(_fields, _attributeNames, context, _fieldName, matching_elements);
}

}
