// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>

namespace proton {

/**
 * Interface for a summary manager.
 */
class ISummaryManager
{
public:
    ISummaryManager(const ISummaryManager &) = delete;
    ISummaryManager & operator = (const ISummaryManager &) = delete;
    /**
     * Interface for a summary setup.
     */
    class ISummarySetup : public search::docsummary::IDocsumEnvironment {
    public:
        typedef std::unique_ptr<ISummarySetup> UP;
        typedef std::shared_ptr<ISummarySetup> SP;

        virtual ~ISummarySetup() {}

        virtual search::docsummary::IDocsumWriter &getDocsumWriter() const = 0;
        virtual search::docsummary::ResultConfig &getResultConfig() = 0;
        virtual search::docsummary::IDocsumStore::UP createDocsumStore(const vespalib::string &resultClassName) = 0;

        // Inherit doc from IDocsumEnvironment
        virtual search::IAttributeManager *getAttributeManager() override = 0;
        virtual vespalib::string lookupIndex(const vespalib::string & s) const override = 0;
        virtual juniper::Juniper *getJuniper() override = 0;
    };

    typedef std::unique_ptr<ISummaryManager> UP;
    typedef std::shared_ptr<ISummaryManager> SP;

    virtual ~ISummaryManager() {}

    virtual ISummarySetup::SP
    createSummarySetup(const vespa::config::search::SummaryConfig &summaryCfg,
                       const vespa::config::search::SummarymapConfig &summarymapCfg,
                       const vespa::config::search::summary::JuniperrcConfig &juniperCfg,
                       const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                       const std::shared_ptr<search::IAttributeManager> &attributeMgr) = 0;

    virtual search::IDocumentStore &getBackingStore() = 0;
protected:
    ISummaryManager() = default;
};

} // namespace proton

