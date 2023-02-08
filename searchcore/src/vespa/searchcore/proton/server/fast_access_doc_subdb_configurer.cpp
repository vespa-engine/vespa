// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_doc_subdb_configurer.h"
#include "document_subdb_reconfig.h"
#include "documentdbconfig.h"
#include "reconfig_params.h"
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/attribute_manager_reconfig.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>

using document::DocumentTypeRepo;
using search::index::Schema;

namespace proton {

using ARIConfig = AttributeReprocessingInitializer::Config;

void
FastAccessDocSubDBConfigurer::reconfigureFeedView(FastAccessFeedView & curr,
                                                  Schema::SP schema,
                                                  std::shared_ptr<const DocumentTypeRepo> repo,
                                                  IAttributeWriter::SP writer)
{
    _feedView.set(std::make_shared<FastAccessFeedView>(
            StoreOnlyFeedView::Context(curr.getSummaryAdapter(),
                                       std::move(schema),
                                       curr.getDocumentMetaStore(),
                                       std::move(repo),
                                       curr.getUncommittedLidTracker(),
                                       curr.getGidToLidChangeHandler(),
                                       curr.getWriteService()),
            curr.getPersistentParams(),
            FastAccessFeedView::Context(std::move(writer),curr.getDocIdLimit())));
}

FastAccessDocSubDBConfigurer::FastAccessDocSubDBConfigurer(FeedViewVarHolder &feedView,
                                                           const vespalib::string &subDbName)
    : _feedView(feedView),
      _subDbName(subDbName)
{
}

FastAccessDocSubDBConfigurer::~FastAccessDocSubDBConfigurer() = default;

std::unique_ptr<DocumentSubDBReconfig>
FastAccessDocSubDBConfigurer::prepare_reconfig(const DocumentDBConfig& new_config_snapshot,
                                               const DocumentDBConfig& old_config_snapshot,
                                               AttributeCollectionSpec && attr_spec,
                                               const ReconfigParams& reconfig_params,
                                               std::optional<search::SerialNum> serial_num)
{
    (void) new_config_snapshot;
    (void) old_config_snapshot;
    (void) serial_num;
    auto old_attribute_writer = _feedView.get()->getAttributeWriter();
    auto old_attribute_manager = old_attribute_writer->getAttributeManager();
    auto reconfig = std::make_unique<DocumentSubDBReconfig>(std::shared_ptr<Matchers>(), old_attribute_manager);
    if (reconfig_params.shouldAttributeManagerChange()) {
        reconfig->set_attribute_manager_reconfig(old_attribute_manager->prepare_create(std::move(attr_spec)));
    }
    return reconfig;
}

IReprocessingInitializer::UP
FastAccessDocSubDBConfigurer::reconfigure(const DocumentDBConfig &newConfig,
                                          const DocumentDBConfig &oldConfig,
                                          const DocumentSubDBReconfig& prepared_reconfig,
                                          search::SerialNum serial_num)
{
    FastAccessFeedView::SP oldView = _feedView.get();
    auto writer = std::make_shared<AttributeWriter>(prepared_reconfig.attribute_manager());
    reconfigureFeedView(*oldView, newConfig.getSchemaSP(), newConfig.getDocumentTypeRepoSP(), writer);

    const document::DocumentType *newDocType = newConfig.getDocumentType();
    const document::DocumentType *oldDocType = oldConfig.getDocumentType();
    assert(newDocType != nullptr);
    assert(oldDocType != nullptr);
    DocumentTypeInspector inspector(*oldDocType, *newDocType);
    IndexschemaInspector oldIndexschemaInspector(oldConfig.getIndexschemaConfig());
    return std::make_unique<AttributeReprocessingInitializer>
        (ARIConfig(writer->getAttributeManager(), *newConfig.getSchemaSP()),
         ARIConfig(oldView->getAttributeWriter()->getAttributeManager(), *oldConfig.getSchemaSP()),
         inspector, oldIndexschemaInspector, _subDbName, serial_num);
}

} // namespace proton
