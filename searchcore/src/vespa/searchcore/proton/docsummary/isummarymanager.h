// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>

namespace document { class DocumentTypeRepo; }
namespace search::index { class Schema; }

namespace proton {

/**
 * Interface for a summary manager.
 */
class ISummaryManager
{
public:
    using SummaryConfig = const vespa::config::search::internal::InternalSummaryType;
    using JuniperrcConfig = const vespa::config::search::summary::internal::InternalJuniperrcType;
    ISummaryManager(const ISummaryManager &) = delete;
    ISummaryManager & operator = (const ISummaryManager &) = delete;
    /**
     * Interface for a summary setup.
     */
    class ISummarySetup : public search::docsummary::IDocsumEnvironment {
    public:
        using UP = std::unique_ptr<ISummarySetup>;
        using SP = std::shared_ptr<ISummarySetup>;

        ~ISummarySetup() override = default;

        virtual search::docsummary::IDocsumWriter &getDocsumWriter() const = 0;
        virtual const search::docsummary::ResultConfig &getResultConfig() = 0;
        virtual search::docsummary::IDocsumStore::UP createDocsumStore() = 0;
    };

    using UP = std::unique_ptr<ISummaryManager>;
    using SP = std::shared_ptr<ISummaryManager>;

    virtual ~ISummaryManager() = default;

    virtual ISummarySetup::SP
    createSummarySetup(const SummaryConfig &summaryCfg,
                       const JuniperrcConfig &juniperCfg,
                       const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                       const std::shared_ptr<search::IAttributeManager> &attributeMgr,
                       const search::index::Schema& schema) = 0;

    virtual search::IDocumentStore &getBackingStore() = 0;
protected:
    ISummaryManager() = default;
};

} // namespace proton

