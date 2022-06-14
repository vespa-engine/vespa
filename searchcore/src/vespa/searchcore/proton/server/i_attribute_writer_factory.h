// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/attribute/attribute_collection_spec.h>
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>

namespace proton {

/**
 * Interface for a factory for creating new IAttributeWriter instances during reconfig.
 */
struct IAttributeWriterFactory
{
    using UP = std::unique_ptr<IAttributeWriterFactory>;
    virtual ~IAttributeWriterFactory() = default;
    virtual IAttributeWriter::SP create(const IAttributeWriter::SP &old,
                                        AttributeCollectionSpec && attrSpec) const = 0;
};

} // namespace proton
