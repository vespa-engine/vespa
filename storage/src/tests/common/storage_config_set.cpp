// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_config_set.h"
#include <vespa/config-bucketspaces.h>
#include <vespa/config-persistence.h>
#include <vespa/config-slobroks.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config-stor-filestor.h>
#include <vespa/config-upgrading.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/metrics/config-metricsmanager.h>
#include <vespa/storage/config/config-stor-bouncer.h>
#include <vespa/storage/config/config-stor-communicationmanager.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-prioritymapping.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/config/config-stor-status.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>
#include <vespa/storage/visiting/config-stor-visitor.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace storage {

StorageConfigSet::StorageConfigSet(vespalib::string config_id_str, bool is_storage_node)
    : _document_type_config(std::make_unique<DocumenttypesConfigBuilder>()),
      _slobroks_config(std::make_unique<SlobroksConfigBuilder>()),
      _messagebus_config(std::make_unique<MessagebusConfigBuilder>()),
      _metrics_config(std::make_unique<MetricsmanagerConfigBuilder>()),
      _persistence_config(std::make_unique<PersistenceConfigBuilder>()),
      _distribution_config(std::make_unique<StorDistributionConfigBuilder>()),
      _filestor_config(std::make_unique<StorFilestorConfigBuilder>()),
      _upgrading_config(std::make_unique<UpgradingConfigBuilder>()),
      _bucket_spaces_config(std::make_unique<BucketspacesConfigBuilder>()),
      _bouncer_config(std::make_unique<StorBouncerConfigBuilder>()),
      _communication_manager_config(std::make_unique<StorCommunicationmanagerConfigBuilder>()),
      _distributor_manager_config(std::make_unique<StorDistributormanagerConfigBuilder>()),
      _priority_mapping_config(std::make_unique<StorPrioritymappingConfigBuilder>()),
      _server_config(std::make_unique<StorServerConfigBuilder>()),
      _status_config(std::make_unique<StorStatusConfigBuilder>()),
      _visitor_config(std::make_unique<StorVisitorConfigBuilder>()),
      _visitor_dispatcher_config(std::make_unique<StorVisitordispatcherConfigBuilder>()),
      _config_id_str(std::move(config_id_str)),
      _config_ctx(std::make_shared<config::ConfigContext>(_config_set)),
      _config_uri(_config_id_str, _config_ctx)
{
    _config_set.addBuilder(_config_id_str, _document_type_config.get());
    _config_set.addBuilder(_config_id_str, _slobroks_config.get());
    _config_set.addBuilder(_config_id_str, _messagebus_config.get());
    _config_set.addBuilder(_config_id_str, _metrics_config.get());
    _config_set.addBuilder(_config_id_str, _persistence_config.get());
    _config_set.addBuilder(_config_id_str, _distribution_config.get());
    _config_set.addBuilder(_config_id_str, _filestor_config.get());
    _config_set.addBuilder(_config_id_str, _upgrading_config.get());
    _config_set.addBuilder(_config_id_str, _bucket_spaces_config.get());
    _config_set.addBuilder(_config_id_str, _bouncer_config.get());
    _config_set.addBuilder(_config_id_str, _communication_manager_config.get());
    _config_set.addBuilder(_config_id_str, _distributor_manager_config.get());
    _config_set.addBuilder(_config_id_str, _priority_mapping_config.get());
    _config_set.addBuilder(_config_id_str, _server_config.get());
    _config_set.addBuilder(_config_id_str, _status_config.get());
    _config_set.addBuilder(_config_id_str, _visitor_config.get());
    _config_set.addBuilder(_config_id_str, _visitor_dispatcher_config.get());

    init_default_configs(is_storage_node);
    _config_ctx->reload();
}

StorageConfigSet::~StorageConfigSet() = default;

void StorageConfigSet::init_default_configs(bool is_storage_node) {
    // Most configs are left with their default values, with explicit values being a
    // union of the legacy DirConfig test helpers.
    *_document_type_config = document::TestDocRepo().getTypeConfig();

    add_metric_consumer("status", {"*"});
    add_metric_consumer("statereporter", {"*"});

    add_distribution_config(50);
    add_bucket_space_mapping("testdoctype1", "default");

    _communication_manager_config->rpcport  = 0;
    _communication_manager_config->mbusport = 0;

    _distributor_manager_config->splitcount = 1000;
    _distributor_manager_config->splitsize  = 10000000;
    _distributor_manager_config->joincount  = 500;
    _distributor_manager_config->joinsize   = 5000000;
    _distributor_manager_config->maxClusterClockSkewSec = 0;

    _filestor_config->numThreads = 1;
    _filestor_config->numResponseThreads = 1;

    _persistence_config->abortOperationsWithChangedBucketOwnership = true;

    _server_config->clusterName = "storage";
    _server_config->nodeIndex = 0;
    _server_config->isDistributor = !is_storage_node;
    _server_config->maxMergesPerNode = 25;
    _server_config->maxMergeQueueSize = 20;
    _server_config->resourceExhaustionMergeBackPressureDurationSecs = 15.0;
    _server_config->writePidFileOnStartup = false;

    _status_config->httpport = 0;

    _visitor_config->maxconcurrentvisitorsFixed = 4;
    _visitor_config->maxconcurrentvisitorsVariable = 0;
}

void StorageConfigSet::add_bucket_space_mapping(vespalib::string doc_type, vespalib::string bucket_space_name) {
    BucketspacesConfigBuilder::Documenttype type;
    type.name = std::move(doc_type);
    type.bucketspace = std::move(bucket_space_name);
    _bucket_spaces_config->documenttype.emplace_back(std::move(type));
}

void StorageConfigSet::add_distribution_config(uint16_t nodes_in_top_level_group) {
    StorDistributionConfigBuilder::Group group;
    group.name = "invalid";
    group.index = "invalid";
    for (uint16_t i = 0; i < nodes_in_top_level_group; ++i) {
        StorDistributionConfigBuilder::Group::Nodes node;
        node.index = i;
        group.nodes.emplace_back(std::move(node));
    }
    _distribution_config->group.clear();
    _distribution_config->group.emplace_back(std::move(group));
    _distribution_config->redundancy = 2;
}

void StorageConfigSet::add_metric_consumer(vespalib::string name, const std::vector<vespalib::string>& added_metrics) {
    MetricsmanagerConfigBuilder::Consumer consumer;
    consumer.name = std::move(name);
    consumer.addedmetrics.assign(added_metrics.begin(), added_metrics.end());
    _metrics_config->consumer.emplace_back(std::move(consumer));
}

void StorageConfigSet::set_node_index(uint16_t node_index) {
    _server_config->nodeIndex = node_index;
}

void StorageConfigSet::set_slobrok_config_port(int slobrok_port) {
    SlobroksConfigBuilder::Slobrok slobrok;
    slobrok.connectionspec = vespalib::make_string("tcp/localhost:%d", slobrok_port);
    _slobroks_config->slobrok.clear();
    _slobroks_config->slobrok.emplace_back(std::move(slobrok));
}

} // storage
