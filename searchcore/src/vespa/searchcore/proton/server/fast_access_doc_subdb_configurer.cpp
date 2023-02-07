// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_doc_subdb_configurer.h"
#include "document_subdb_reconfig.h"
#include "i_attribute_writer_factory.h"
#include "documentdbconfig.h"
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
                                                           IAttributeWriterFactory::UP factory,
                                                           const vespalib::string &subDbName)
    : _feedView(feedView),
      _factory(std::move(factory)),
      _subDbName(subDbName)
{
}

FastAccessDocSubDBConfigurer::~FastAccessDocSubDBConfigurer() = default;

std::unique_ptr<DocumentSubDBReconfig>
FastAccessDocSubDBConfigurer::prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const DocumentDBConfig& old_config_snapshot, const ReconfigParams& reconfig_params, std::optional<search::SerialNum> serial_num)
{
    (void) new_config_snapshot;
    (void) old_config_snapshot;
    (void) reconfig_params;
    (void) serial_num;
    return std::make_unique<DocumentSubDBReconfig>(std::shared_ptr<Matchers>());
}

IReprocessingInitializer::UP
FastAccessDocSubDBConfigurer::reconfigure(const DocumentDBConfig &newConfig,
                                          const DocumentDBConfig &oldConfig,
                                          AttributeCollectionSpec && attrSpec,
                                          const DocumentSubDBReconfig& prepared_reconfig,
                                          search::SerialNum serial_num)
{
    (void) prepared_reconfig;
    FastAccessFeedView::SP oldView = _feedView.get();
    auto& attr_spec_serial_num = attrSpec.getCurrentSerialNum();
    assert(!attr_spec_serial_num.has_value() || attr_spec_serial_num.value() == serial_num);
    IAttributeWriter::SP writer = _factory->create(oldView->getAttributeWriter(), std::move(attrSpec));
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
