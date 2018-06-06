// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_attribute_combiner_dfw.h"
#include "docsum_field_writer_state.h"
#include "attribute_field_writer.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/data/slime/cursor.h>

using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using vespalib::slime::Cursor;

namespace search::docsummary {

namespace {

class ArrayAttributeFieldWriterState : public DocsumFieldWriterState
{
    std::vector<std::unique_ptr<AttributeFieldWriter>> _writers;

public:
    ArrayAttributeFieldWriterState(const std::vector<vespalib::string> &fieldNames,
                                   const std::vector<vespalib::string> &attributeNames,
                                   IAttributeContext &context);
    ~ArrayAttributeFieldWriterState() override;
    void insertField(uint32_t docId, vespalib::slime::Inserter &target) override;
};

ArrayAttributeFieldWriterState::ArrayAttributeFieldWriterState(const std::vector<vespalib::string> &fieldNames,
                                                               const std::vector<vespalib::string> &attributeNames,
                                                               IAttributeContext &context)
    : DocsumFieldWriterState()
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
ArrayAttributeFieldWriterState::insertField(uint32_t docId, vespalib::slime::Inserter &target)
{
    uint32_t elems = 0;
    for (auto &writer : _writers) {
        writer->fetch(docId);
        if (elems < writer->size()) {
            elems = writer->size();
        }
    }
    Cursor &arr = target.insertArray();
    for (uint32_t idx = 0; idx < elems; ++idx) {
        Cursor &obj = arr.addObject();
        for (auto &writer : _writers) {
            writer->print(idx, obj);
        }
    }
}

}

ArrayAttributeCombinerDFW::ArrayAttributeCombinerDFW(const vespalib::string &fieldName,
                                                     const std::vector<vespalib::string> &fields)
    : AttributeCombinerDFW(fieldName),
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
ArrayAttributeCombinerDFW::allocFieldWriterState(IAttributeContext &context)
{
    return std::make_unique<ArrayAttributeFieldWriterState>(_fields, _attributeNames, context);
}

}
