// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer.h"
#include "attribute_writer_explorer.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/data/slime/cursor.h>

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace proton {

AttributeWriterExplorer::AttributeWriterExplorer(std::shared_ptr<IAttributeWriter> writer)
    : _writer(std::move(writer))
{
}

AttributeWriterExplorer::~AttributeWriterExplorer() = default;

namespace {

vespalib::string
type_to_string(const Config& cfg)
{
    if (cfg.basicType().type() == BasicType::TENSOR) {
        return cfg.tensorType().to_spec();
    }
    if (cfg.collectionType().type() == CollectionType::SINGLE) {
        return cfg.basicType().asString();
    }
    return vespalib::string(cfg.collectionType().asString()) +
        "<" + vespalib::string(cfg.basicType().asString()) + ">";
}

void
convert_to_slime(const AttributeWriter::WriteContext& context, Cursor& object)
{
    object.setLong("executor_id", context.getExecutorId().getId());
    Cursor& fields = object.setArray("attribute_fields");
    for (const auto& field : context.getFields()) {
        Cursor& f = fields.addObject();
        f.setString("name", field.getAttribute().getName());
        f.setString("type", type_to_string(field.getAttribute().getConfig()));
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
