// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer.h"
#include "attribute_writer_explorer.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace proton {

AttributeWriterExplorer::AttributeWriterExplorer(std::shared_ptr<IAttributeWriter> writer)
    : _writer(std::move(writer))
{
}

AttributeWriterExplorer::~AttributeWriterExplorer() = default;

namespace {

void
convert_to_slime(const AttributeWriter::WriteContext& context, Cursor& object)
{
    object.setLong("executor_id", context.getExecutorId().getId());
    Cursor& fields = object.setArray("fields");
    for (const auto& field : context.getFields()) {
        fields.addString(field.getAttribute().getName());
    }
}

}

void
AttributeWriterExplorer::get_state(const Inserter& inserter, bool full) const
{
    Cursor& object = inserter.insertObject();
    if (full) {
        auto* writer = dynamic_cast<AttributeWriter*>(_writer.get());
        if (writer) {
            Cursor& contexts = object.setArray("write_contexts");
            for (const auto& context : writer->get_write_contexts()) {
                convert_to_slime(context, contexts.addObject());
            }
        }
    }
}

}
