// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_spec.h"

namespace document { class DocumentType; }
namespace vespa { namespace config { namespace search { namespace internal {
class InternalAttributesType;
class InternalIndexschemaType;
} } } }

namespace proton {

class IDocumentTypeInspector;

/*
 * Class to build adjusted attribute config and vector of attribute specs.
 */
class AttributeSpecsBuilder
{
    using AttributeSpecs = std::vector<AttributeSpec>;
    using AttributesConfigBuilder = vespa::config::search::internal::InternalAttributesType;
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    using DocumentType = document::DocumentType;
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;

    AttributeSpecs _specs;
    std::shared_ptr<AttributesConfigBuilder> _config;

public:
    AttributeSpecsBuilder();
    ~AttributeSpecsBuilder();

    void setup(const AttributesConfig &newConfig);
    void setup(const AttributesConfig &oldAttributesConfig,
               const AttributesConfig &newAttributesConfig,
               const IndexschemaConfig &oldIndexschemaConfig,
               const IDocumentTypeInspector &inspector);

    const AttributeSpecs &getAttributeSpecs() const;
    std::shared_ptr<AttributesConfig> getAttributesConfig() const;
};

} // namespace proton
