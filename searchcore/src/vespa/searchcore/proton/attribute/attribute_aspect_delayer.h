// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace document { class DocumentType; }
namespace vespa::config::search::internal {
class InternalAttributesType;
class InternalIndexschemaType;
class InternalSummaryType;
}

namespace proton {

struct IDocumentTypeInspector;
class IIndexschemaInspector;

/**
 * Class to build adjusted attributes config and summary map config
 * to eliminate need for reprocessing when system is online.
 */
class AttributeAspectDelayer
{
    using AttributesConfigBuilder = vespa::config::search::internal::InternalAttributesType;
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    using DocumentType = document::DocumentType;
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;
    using SummaryConfigBuilder = vespa::config::search::internal::InternalSummaryType;
    using SummaryConfig = const vespa::config::search::internal::InternalSummaryType;

    std::shared_ptr<AttributesConfigBuilder> _attributesConfig;
    std::shared_ptr<SummaryConfigBuilder>    _summaryConfig;

public:
    AttributeAspectDelayer();
    ~AttributeAspectDelayer();

    /*
     * Setup to avoid reprocessing, used to create adjusted document db
     * config before applying new config when system is online.
     */
    void setup(const AttributesConfig &oldAttributesConfig,
               const AttributesConfig &newAttributesConfig,
               const SummaryConfig &newSummaryConfig,
               const IIndexschemaInspector &oldIndexschemaInspector,
               const IDocumentTypeInspector &inspector);

    std::shared_ptr<AttributesConfig> getAttributesConfig() const;
    std::shared_ptr<SummaryConfig> getSummaryConfig() const;
};

} // namespace proton
