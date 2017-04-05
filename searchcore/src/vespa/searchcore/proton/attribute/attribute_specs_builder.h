// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace document { class DocumentType; }
namespace vespa { namespace config { namespace search { namespace internal {
class InternalAttributesType;
class InternalIndexschemaType;
} } } }

namespace proton {

class IDocumentTypeInspector;
class AttributeSpecs;

/*
 * Class to build adjusted attribute config and vector of attribute specs
 * to eliminate need for reprocessing when system is online.
 */
class AttributeSpecsBuilder
{
    using AttributesConfigBuilder = vespa::config::search::internal::InternalAttributesType;
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    using DocumentType = document::DocumentType;
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;

    std::shared_ptr<AttributeSpecs> _specs;
    std::shared_ptr<AttributesConfigBuilder> _config;

public:
    AttributeSpecsBuilder();
    ~AttributeSpecsBuilder();

    /*
     * Setup called from document db config manager and document db
     * config scout.  No adjustments.
     */
    void setup(const AttributesConfig &newConfig);
    /*
     * Setup to avoid reprocessing, used to create adjusted document db
     * config before applying new config when system is online.
     */
    void setup(const AttributesConfig &oldAttributesConfig,
               const AttributesConfig &newAttributesConfig,
               const IndexschemaConfig &oldIndexschemaConfig,
               const IDocumentTypeInspector &inspector);

    std::shared_ptr<const AttributeSpecs> getAttributeSpecs() const;
    std::shared_ptr<AttributesConfig> getAttributesConfig() const;
};

} // namespace proton
