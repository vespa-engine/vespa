// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "struct_map_attribute_combiner_dfw.h"
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

vespalib::Memory keyName("key");
vespalib::Memory valueName("value");

class StructMapAttributeFieldWriterState : public DocsumFieldWriterState
{
    std::unique_ptr<AttributeFieldWriter> _keyWriter;
    std::vector<std::unique_ptr<AttributeFieldWriter>> _writers;

public:
    StructMapAttributeFieldWriterState(const vespalib::string &keyAttributeName,
                                       const std::vector<vespalib::string> &fieldNames,
                                       const std::vector<vespalib::string> &attributeNames,
                                       IAttributeContext &context);
    ~StructMapAttributeFieldWriterState() override;
    void insertField(uint32_t docId, vespalib::slime::Inserter &target) override;
};

StructMapAttributeFieldWriterState::StructMapAttributeFieldWriterState(const vespalib::string &keyAttributeName,
                                                                       const std::vector<vespalib::string> &fieldNames,
                                                                       const std::vector<vespalib::string> &attributeNames,
                                                                       IAttributeContext &context)
    : DocsumFieldWriterState(),
      _keyWriter(),
      _writers()
{
    const IAttributeVector *attr = context.getAttribute(keyAttributeName);
    if (attr != nullptr) {
        _keyWriter = AttributeFieldWriter::create(keyName, *attr);
    }
    size_t fields = fieldNames.size();
    _writers.reserve(fields);
    for (uint32_t field = 0; field < fields; ++field) {
        attr = context.getAttribute(attributeNames[field]);
        if (attr != nullptr) {
            _writers.emplace_back(AttributeFieldWriter::create(fieldNames[field], *attr));
        }
    }
}

StructMapAttributeFieldWriterState::~StructMapAttributeFieldWriterState() = default;

void
StructMapAttributeFieldWriterState::insertField(uint32_t docId, vespalib::slime::Inserter &target)
{
    uint32_t elems = 0;
    if (_keyWriter) {
        _keyWriter->fetch(docId);
        if (elems < _keyWriter->size()) {
            elems = _keyWriter->size();
        }
    }
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
    for (uint32_t idx = 0; idx < elems; ++idx) {
        Cursor &keyValueObj = arr.addObject();
        if (_keyWriter) {
            _keyWriter->print(idx, keyValueObj);
        }
        Cursor &obj = keyValueObj.setObject(valueName);
        for (auto &writer : _writers) {
            writer->print(idx, obj);
        }
    }
}

}

StructMapAttributeCombinerDFW::StructMapAttributeCombinerDFW(const vespalib::string &fieldName,
                                                             const std::vector<vespalib::string> &fields)
    : AttributeCombinerDFW(fieldName),
      _keyAttributeName(),
      _fields(fields),
      _attributeNames()
{
    _keyAttributeName = fieldName + ".key";
    _attributeNames.reserve(_fields.size());
    vespalib::string prefix = fieldName + ".value.";
    for (const auto &field : _fields) {
        _attributeNames.emplace_back(prefix + field);
    }
}

StructMapAttributeCombinerDFW::~StructMapAttributeCombinerDFW() = default;

std::unique_ptr<DocsumFieldWriterState>
StructMapAttributeCombinerDFW::allocFieldWriterState(IAttributeContext &context)
{
    return std::make_unique<StructMapAttributeFieldWriterState>(_keyAttributeName, _fields, _attributeNames, context);
}

}
