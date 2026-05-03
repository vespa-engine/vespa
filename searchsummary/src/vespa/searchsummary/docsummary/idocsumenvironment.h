// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/iattributemanager.h>

namespace juniper {
class Juniper;
}
namespace search {
class IDocumentIdProvider;
}

namespace search::docsummary {

/**
 * Abstract view of information available to rewriters for generating docsum fields.
 **/
class IDocsumEnvironment {
public:
    virtual ~IDocsumEnvironment() = default;
    virtual const search::IAttributeManager* getAttributeManager() const = 0;
    virtual const juniper::Juniper* getJuniper() const = 0;
    [[nodiscard]] virtual std::shared_ptr<const IDocumentIdProvider> get_document_id_provider() const noexcept = 0;
};

} // namespace search::docsummary
