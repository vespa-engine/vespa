// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster_controller.h"
#include "bm_message_bus.h"
#include "bm_storage_chain_builder.h"
#include "bm_storage_link_context.h"
#include "pending_tracker.h"
#include "spi_bm_feed_handler.h"
#include "storage_api_chain_bm_feed_handler.h"
#include "storage_api_message_bus_bm_feed_handler.h"
#include "storage_api_rpc_bm_feed_handler.h"
#include "document_api_message_bus_bm_feed_handler.h"
#include <tests/proton/common/dummydbowner.h>
#include <vespa/config-attributes.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-persistence.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-slobroks.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config-stor-filestor.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/config-upgrading.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/fastos/app.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/metrics/config-metricsmanager.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_db_maintenance_config.h>
#include <vespa/searchcore/proton/server/documentdb.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/fileconfigmanager.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/server/persistencehandlerproxy.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storage/bucketdb/config-stor-bucket-init.h>
#include <vespa/storage/common/i_storage_chain_builder.h>
#include <vespa/storage/config/config-stor-bouncer.h>
#include <vespa/storage/config/config-stor-communicationmanager.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-opslogger.h>
#include <vespa/storage/config/config-stor-prioritymapping.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/config/config-stor-status.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/visiting/config-stor-visitor.h>
#include <vespa/storageserver/app/distributorprocess.h>
#include <vespa/storageserver/app/servicelayerprocess.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <getopt.h>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("vespa-feed-bm");

using namespace cloud::config::filedistribution;
using namespace config;
using namespace proton;
using namespace std::chrono_literals;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using vespa::config::content::PersistenceConfigBuilder;
using vespa::config::content::StorDistributionConfigBuilder;
using vespa::config::content::StorFilestorConfigBuilder;
using vespa::config::content::UpgradingConfigBuilder;
using vespa::config::content::core::BucketspacesConfig;
using vespa::config::content::core::BucketspacesConfigBuilder;
using vespa::config::content::core::StorBouncerConfigBuilder;
using vespa::config::content::core::StorBucketInitConfigBuilder;
using vespa::config::content::core::StorCommunicationmanagerConfigBuilder;
using vespa::config::content::core::StorDistributormanagerConfigBuilder;
using vespa::config::content::core::StorOpsloggerConfigBuilder;
using vespa::config::content::core::StorPrioritymappingConfigBuilder;
using vespa::config::content::core::StorServerConfigBuilder;
using vespa::config::content::core::StorStatusConfigBuilder;
using vespa::config::content::core::StorVisitorConfigBuilder;
using vespa::config::content::core::StorVisitordispatcherConfigBuilder;
using cloud::config::SlobroksConfigBuilder;
using messagebus::MessagebusConfigBuilder;
using metrics::MetricsmanagerConfigBuilder;

using config::ConfigContext;
using config::ConfigSet;
using config::ConfigUri;
using config::IConfigContext;
using document::AssignValueUpdate;
using document::BucketId;
using document::BucketSpace;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using document::DocumentUpdate;
using document::DocumenttypesConfig;
using document::DocumenttypesConfigBuilder;
using document::Field;
using document::FieldSetRepo;
using document::FieldUpdate;
using document::IntFieldValue;
using document::test::makeBucketSpace;
using feedbm::BmClusterController;
using feedbm::BmMessageBus;
using feedbm::BmStorageChainBuilder;
using feedbm::BmStorageLinkContext;
using feedbm::IBmFeedHandler;
using feedbm::DocumentApiMessageBusBmFeedHandler;
using feedbm::SpiBmFeedHandler;
using feedbm::StorageApiChainBmFeedHandler;
using feedbm::StorageApiMessageBusBmFeedHandler;
using feedbm::StorageApiRpcBmFeedHandler;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::index::SchemaBuilder;
using search::transactionlog::TransLogServer;
using storage::rpc::SharedRpcResources;
using storage::rpc::StorageApiRpcService;
using storage::spi::PersistenceProvider;
using vespalib::compression::CompressionConfig;
using vespalib::makeLambdaTask;
using proton::ThreadingServiceConfig;

using DocumentDBMap = std::map<DocTypeName, std::shared_ptr<DocumentDB>>;

namespace {

vespalib::string base_dir = "testdb";

std::shared_ptr<DocumenttypesConfig> make_document_type() {
    using Struct = document::config_builder::Struct;
    using DataType = document::DataType;
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "test", Struct("test.header").addField("int", DataType::T_INT), Struct("test.body"));
    return std::make_shared<DocumenttypesConfig>(builder.config());
}

std::shared_ptr<AttributesConfig> make_attributes_config() {
    AttributesConfigBuilder builder;
    AttributesConfig::Attribute attribute;
    attribute.name = "int";
    attribute.datatype = AttributesConfig::Attribute::Datatype::INT32;
    builder.attribute.emplace_back(attribute);
    return std::make_shared<AttributesConfig>(builder);
}

std::shared_ptr<DocumentDBConfig> make_document_db_config(std::shared_ptr<DocumenttypesConfig> document_types, std::shared_ptr<const DocumentTypeRepo> repo, const DocTypeName& doc_type_name)
{
    auto indexschema = std::make_shared<IndexschemaConfig>();
    auto attributes = make_attributes_config();
    auto summary = std::make_shared<SummaryConfig>();
    std::shared_ptr<Schema> schema(new Schema());
    SchemaBuilder::build(*indexschema, *schema);
    SchemaBuilder::build(*attributes, *schema);
    SchemaBuilder::build(*summary, *schema);
    return std::make_shared<DocumentDBConfig>(
            1,
            std::make_shared<RankProfilesConfig>(),
            std::make_shared<matching::RankingConstants>(),
            std::make_shared<matching::OnnxModels>(),
            indexschema,
            attributes,
            summary,
            std::make_shared<SummarymapConfig>(),
            std::make_shared<JuniperrcConfig>(),
            document_types,
            repo,
            std::make_shared<ImportedFieldsConfig>(),
            std::make_shared<TuneFileDocumentDB>(),
            schema,
            std::make_shared<DocumentDBMaintenanceConfig>(),
            search::LogDocumentStore::Config(),
            std::make_shared<const ThreadingServiceConfig>(ThreadingServiceConfig::make(1)),
            std::make_shared<const AllocConfig>(),
            "client",
            doc_type_name.getName());
}

void
make_slobroks_config(SlobroksConfigBuilder& slobroks, int slobrok_port)
{
    SlobroksConfigBuilder::Slobrok slobrok;
    slobrok.connectionspec = vespalib::make_string("tcp/localhost:%d", slobrok_port);
    slobroks.slobrok.push_back(std::move(slobrok));
}

void
make_bucketspaces_config(BucketspacesConfigBuilder &bucketspaces)
{
    BucketspacesConfigBuilder::Documenttype bucket_space_map;
    bucket_space_map.name = "test";
    bucket_space_map.bucketspace = "default";
    bucketspaces.documenttype.emplace_back(std::move(bucket_space_map));
}

class MyPersistenceEngineOwner : public IPersistenceEngineOwner
{
    void setClusterState(BucketSpace, const storage::spi::ClusterState &) override { }
};

struct MyResourceWriteFilter : public IResourceWriteFilter
{
    bool acceptWriteOperation() const override { return true; }
    State getAcceptState() const override { return IResourceWriteFilter::State(); }
};

class BucketSelector
{
    uint32_t _thread_id;
    uint32_t _threads;
    uint32_t _num_buckets;
public:
    BucketSelector(uint32_t thread_id_in, uint32_t threads_in, uint32_t num_buckets_in)
        : _thread_id(thread_id_in),
          _threads(threads_in),
          _num_buckets((num_buckets_in / _threads) * _threads)
    {
    }
    uint64_t operator()(uint32_t i) const {
        return (static_cast<uint64_t>(i) * _threads + _thread_id) % _num_buckets;
    }
};

class BMRange
{
    uint32_t _start;
    uint32_t _end;
public:
    BMRange(uint32_t start_in, uint32_t end_in)
        : _start(start_in),
          _end(end_in)
    {
    }
    uint32_t get_start() const { return _start; }
    uint32_t get_end() const { return _end; }
};

class BMParams {
    uint32_t _documents;
    uint32_t _client_threads;
    uint32_t _get_passes;
    vespalib::string _indexing_sequencer;
    uint32_t _put_passes;
    uint32_t _update_passes;
    uint32_t _remove_passes;
    uint32_t _rpc_network_threads;
    uint32_t _rpc_events_before_wakeup;
    uint32_t _rpc_targets_per_node;
    uint32_t _response_threads;
    uint32_t _max_pending;
    bool     _enable_distributor;
    bool     _enable_service_layer;
    bool     _skip_get_spi_bucket_info;
    bool     _use_document_api;
    bool     _use_message_bus;
    bool     _use_storage_chain;
    bool     _use_async_message_handling_on_schedule;
    uint32_t _bucket_db_stripe_bits;
    uint32_t get_start(uint32_t thread_id) const {
        return (_documents / _client_threads) * thread_id + std::min(thread_id, _documents % _client_threads);
    }
public:
    BMParams()
        : _documents(160000),
          _client_threads(1),
          _get_passes(0),
          _indexing_sequencer(),
          _put_passes(2),
          _update_passes(1),
          _remove_passes(2),
          _rpc_network_threads(1), // Same default as previous in stor-communicationmanager.def
          _rpc_events_before_wakeup(1), // Same default as in stor-communicationmanager.def
          _rpc_targets_per_node(1), // Same default as in stor-communicationmanager.def
          _response_threads(2), // Same default as in stor-filestor.def
          _max_pending(1000),
          _enable_distributor(false),
          _enable_service_layer(false),
          _skip_get_spi_bucket_info(false),
          _use_document_api(false),
          _use_message_bus(false),
          _use_storage_chain(false),
          _use_async_message_handling_on_schedule(false),
          _bucket_db_stripe_bits(0)
    {
    }
    BMRange get_range(uint32_t thread_id) const {
        return BMRange(get_start(thread_id), get_start(thread_id + 1));
    }
    uint32_t get_documents() const { return _documents; }
    uint32_t get_max_pending() const { return _max_pending; }
    uint32_t get_client_threads() const { return _client_threads; }
    uint32_t get_get_passes() const { return _get_passes; }
    const vespalib::string & get_indexing_sequencer() const { return _indexing_sequencer; }
    uint32_t get_put_passes() const { return _put_passes; }
    uint32_t get_update_passes() const { return _update_passes; }
    uint32_t get_remove_passes() const { return _remove_passes; }
    uint32_t get_rpc_network_threads() const { return _rpc_network_threads; }
    uint32_t get_rpc_events_before_wakup() const { return _rpc_events_before_wakeup; }
    uint32_t get_rpc_targets_per_node() const { return _rpc_targets_per_node; }
    uint32_t get_response_threads() const { return _response_threads; }
    bool get_enable_distributor() const { return _enable_distributor; }
    bool get_skip_get_spi_bucket_info() const { return _skip_get_spi_bucket_info; }
    bool get_use_document_api() const { return _use_document_api; }
    bool get_use_message_bus() const { return _use_message_bus; }
    bool get_use_storage_chain() const { return _use_storage_chain; }
    bool get_use_async_message_handling_on_schedule() const { return _use_async_message_handling_on_schedule; }
    uint32_t get_bucket_db_stripe_bits() const { return _bucket_db_stripe_bits; }
    void set_documents(uint32_t documents_in) { _documents = documents_in; }
    void set_max_pending(uint32_t max_pending_in) { _max_pending = max_pending_in; }
    void set_client_threads(uint32_t threads_in) { _client_threads = threads_in; }
    void set_get_passes(uint32_t get_passes_in) { _get_passes = get_passes_in; }
    void set_indexing_sequencer(vespalib::stringref sequencer) { _indexing_sequencer = sequencer; }
    void set_put_passes(uint32_t put_passes_in) { _put_passes = put_passes_in; }
    void set_update_passes(uint32_t update_passes_in) { _update_passes = update_passes_in; }
    void set_remove_passes(uint32_t remove_passes_in) { _remove_passes = remove_passes_in; }
    void set_rpc_network_threads(uint32_t threads_in) { _rpc_network_threads = threads_in; }
    void set_rpc_events_before_wakeup(uint32_t value) { _rpc_events_before_wakeup = value; }
    void set_rpc_targets_per_node(uint32_t targets_in) { _rpc_targets_per_node = targets_in; }
    void set_response_threads(uint32_t threads_in) { _response_threads = threads_in; }
    void set_enable_distributor(bool value) { _enable_distributor = value; }
    void set_enable_service_layer(bool value) { _enable_service_layer = value; }
    void set_skip_get_spi_bucket_info(bool value) { _skip_get_spi_bucket_info = value; }
    void set_use_document_api(bool value) { _use_document_api = value; }
    void set_use_message_bus(bool value) { _use_message_bus = value; }
    void set_use_storage_chain(bool value) { _use_storage_chain = value; }
    void set_use_async_message_handling_on_schedule(bool value) { _use_async_message_handling_on_schedule = value; }
    void set_bucket_db_stripe_bits(uint32_t value) { _bucket_db_stripe_bits = value; }
    bool check() const;
    bool needs_service_layer() const { return _enable_service_layer || _enable_distributor || _use_storage_chain || _use_message_bus || _use_document_api; }
    bool needs_distributor() const { return _enable_distributor || _use_document_api; }
    bool needs_message_bus() const { return _use_message_bus || _use_document_api; }
};

bool
BMParams::check() const
{
    if (_client_threads < 1) {
        std::cerr << "Too few client threads: " << _client_threads << std::endl;
        return false;
    }
    if (_client_threads > 256) {
        std::cerr << "Too many client threads: " << _client_threads << std::endl;
        return false;
    }
    if (_documents < _client_threads) {
        std::cerr << "Too few documents: " << _documents << std::endl;
        return false;
    }
    if (_put_passes < 1) {
        std::cerr << "Put passes too low: " << _put_passes << std::endl;
        return false;
    }
    if (_rpc_network_threads < 1) {
        std::cerr << "Too few rpc network threads: " << _rpc_network_threads << std::endl;
        return false;
    }
    if (_rpc_targets_per_node < 1) {
        std::cerr << "Too few rpc targets per node: " << _rpc_targets_per_node << std::endl;
        return false;
    }
    if (_response_threads < 1) {
        std::cerr << "Too few response threads: " << _response_threads << std::endl;
        return false;
    }

    return true;
}

class MyServiceLayerProcess : public storage::ServiceLayerProcess {
    PersistenceProvider&    _provider;

public:
    MyServiceLayerProcess(const config::ConfigUri & configUri,
                          PersistenceProvider &provider,
                          std::unique_ptr<storage::IStorageChainBuilder> chain_builder);
    ~MyServiceLayerProcess() override { shutdown(); }

    void shutdown() override;
    void setupProvider() override;
    PersistenceProvider& getProvider() override;
};

MyServiceLayerProcess::MyServiceLayerProcess(const config::ConfigUri & configUri,
                                             PersistenceProvider &provider,
                                             std::unique_ptr<storage::IStorageChainBuilder> chain_builder)
    : ServiceLayerProcess(configUri),
      _provider(provider)
{
    if (chain_builder) {
        set_storage_chain_builder(std::move(chain_builder));
    }
}

void
MyServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
}

void
MyServiceLayerProcess::setupProvider()
{
}

PersistenceProvider&
MyServiceLayerProcess::getProvider()
{
    return _provider;
}

struct MyStorageConfig
{
    vespalib::string              config_id;
    DocumenttypesConfigBuilder    documenttypes;
    StorDistributionConfigBuilder stor_distribution;
    StorBouncerConfigBuilder      stor_bouncer;
    StorCommunicationmanagerConfigBuilder stor_communicationmanager;
    StorOpsloggerConfigBuilder    stor_opslogger;
    StorPrioritymappingConfigBuilder stor_prioritymapping;
    UpgradingConfigBuilder        upgrading;
    StorServerConfigBuilder       stor_server;
    StorStatusConfigBuilder       stor_status;
    BucketspacesConfigBuilder     bucketspaces;
    MetricsmanagerConfigBuilder   metricsmanager;
    SlobroksConfigBuilder         slobroks;
    MessagebusConfigBuilder       messagebus;

    MyStorageConfig(bool distributor, const vespalib::string& config_id_in, const DocumenttypesConfig& documenttypes_in,
                    int slobrok_port, int mbus_port, int rpc_port, int status_port, const BMParams& params)
        : config_id(config_id_in),
          documenttypes(documenttypes_in),
          stor_distribution(),
          stor_bouncer(),
          stor_communicationmanager(),
          stor_opslogger(),
          stor_prioritymapping(),
          upgrading(),
          stor_server(),
          stor_status(),
          bucketspaces(),
          metricsmanager(),
          slobroks(),
          messagebus()
    {
        {
            auto &dc = stor_distribution;
            {
                StorDistributionConfigBuilder::Group group;
                {
                    StorDistributionConfigBuilder::Group::Nodes node;
                    node.index = 0;
                    group.nodes.push_back(std::move(node));
                }
                group.index = "invalid";
                group.name = "invalid";
                group.capacity = 1.0;
                group.partitions = "";
                dc.group.push_back(std::move(group));
            }
            dc.redundancy = 1;
            dc.readyCopies = 1;
        }
        stor_server.isDistributor = distributor;
        stor_server.contentNodeBucketDbStripeBits = params.get_bucket_db_stripe_bits();
        if (distributor) {
            stor_server.rootFolder = "distributor";
        } else {
            stor_server.rootFolder = "storage";
        }
        make_slobroks_config(slobroks, slobrok_port);
        stor_communicationmanager.rpc.numNetworkThreads = params.get_rpc_network_threads();
        stor_communicationmanager.rpc.eventsBeforeWakeup = params.get_rpc_events_before_wakup();
        stor_communicationmanager.rpc.numTargetsPerNode = params.get_rpc_targets_per_node();
        stor_communicationmanager.mbusport = mbus_port;
        stor_communicationmanager.rpcport = rpc_port;

        stor_status.httpport = status_port;
        make_bucketspaces_config(bucketspaces);
    }

    ~MyStorageConfig();

    void add_builders(ConfigSet &set) {
        set.addBuilder(config_id, &documenttypes);
        set.addBuilder(config_id, &stor_distribution);
        set.addBuilder(config_id, &stor_bouncer);
        set.addBuilder(config_id, &stor_communicationmanager);
        set.addBuilder(config_id, &stor_opslogger);
        set.addBuilder(config_id, &stor_prioritymapping);
        set.addBuilder(config_id, &upgrading);
        set.addBuilder(config_id, &stor_server);
        set.addBuilder(config_id, &stor_status);
        set.addBuilder(config_id, &bucketspaces);
        set.addBuilder(config_id, &metricsmanager);
        set.addBuilder(config_id, &slobroks);
        set.addBuilder(config_id, &messagebus);
    }
};

MyStorageConfig::~MyStorageConfig() = default;

struct MyServiceLayerConfig : public MyStorageConfig
{
    PersistenceConfigBuilder      persistence;
    StorFilestorConfigBuilder     stor_filestor;
    StorBucketInitConfigBuilder   stor_bucket_init;
    StorVisitorConfigBuilder      stor_visitor;

    MyServiceLayerConfig(const vespalib::string& config_id_in, const DocumenttypesConfig& documenttypes_in,
                         int slobrok_port, int mbus_port, int rpc_port, int status_port, const BMParams& params)
        : MyStorageConfig(false, config_id_in, documenttypes_in, slobrok_port, mbus_port, rpc_port, status_port, params),
          persistence(),
          stor_filestor(),
          stor_bucket_init(),
          stor_visitor()
    {
        stor_filestor.numResponseThreads = params.get_response_threads();
        stor_filestor.numNetworkThreads = params.get_rpc_network_threads();
        stor_filestor.useAsyncMessageHandlingOnSchedule = params.get_use_async_message_handling_on_schedule();
    }

    ~MyServiceLayerConfig();

    void add_builders(ConfigSet &set) {
        MyStorageConfig::add_builders(set);
        set.addBuilder(config_id, &persistence);
        set.addBuilder(config_id, &stor_filestor);
        set.addBuilder(config_id, &stor_bucket_init);
        set.addBuilder(config_id, &stor_visitor);
    }
};

MyServiceLayerConfig::~MyServiceLayerConfig() = default;

struct MyDistributorConfig : public MyStorageConfig
{
    StorDistributormanagerConfigBuilder stor_distributormanager;
    StorVisitordispatcherConfigBuilder  stor_visitordispatcher;

    MyDistributorConfig(const vespalib::string& config_id_in, const DocumenttypesConfig& documenttypes_in,
                        int slobrok_port, int mbus_port, int rpc_port, int status_port, const BMParams& params)
        : MyStorageConfig(true, config_id_in, documenttypes_in, slobrok_port, mbus_port, rpc_port, status_port, params),
          stor_distributormanager(),
          stor_visitordispatcher()
    {
    }

    ~MyDistributorConfig();

    void add_builders(ConfigSet &set) {
        MyStorageConfig::add_builders(set);
        set.addBuilder(config_id, &stor_distributormanager);
        set.addBuilder(config_id, &stor_visitordispatcher);
    }
};

MyDistributorConfig::~MyDistributorConfig() = default;

struct MyRpcClientConfig {
    vespalib::string      config_id;
    SlobroksConfigBuilder slobroks;

    MyRpcClientConfig(const vespalib::string &config_id_in, int slobrok_port)
        : config_id(config_id_in),
          slobroks()
    {
        make_slobroks_config(slobroks, slobrok_port);
    }
    ~MyRpcClientConfig();

    void add_builders(ConfigSet &set) {
        set.addBuilder(config_id, &slobroks);
    }
};

MyRpcClientConfig::~MyRpcClientConfig() = default;

struct MyMessageBusConfig {
    vespalib::string              config_id;
    SlobroksConfigBuilder         slobroks;
    MessagebusConfigBuilder       messagebus;

    MyMessageBusConfig(const vespalib::string &config_id_in, int slobrok_port)
        : config_id(config_id_in),
          slobroks(),
          messagebus()
    {
        make_slobroks_config(slobroks, slobrok_port);
    }
    ~MyMessageBusConfig();

    void add_builders(ConfigSet &set) {
        set.addBuilder(config_id, &slobroks);
        set.addBuilder(config_id, &messagebus);
    }
};

MyMessageBusConfig::~MyMessageBusConfig() = default;

}

struct PersistenceProviderFixture {
    std::shared_ptr<DocumenttypesConfig>       _document_types;
    std::shared_ptr<const DocumentTypeRepo>    _repo;
    DocTypeName                                _doc_type_name;
    const DocumentType*                        _document_type;
    const Field&                               _field;
    std::shared_ptr<DocumentDBConfig>          _document_db_config;
    vespalib::string                           _base_dir;
    DummyFileHeaderContext                     _file_header_context;
    int                                        _tls_listen_port;
    int                                        _slobrok_port;
    int                                        _rpc_client_port;
    int                                        _service_layer_mbus_port;
    int                                        _service_layer_rpc_port;
    int                                        _service_layer_status_port;
    int                                        _distributor_mbus_port;
    int                                        _distributor_rpc_port;
    int                                        _distributor_status_port;
    TransLogServer                             _tls;
    vespalib::string                           _tls_spec;
    matching::QueryLimiter                     _query_limiter;
    vespalib::Clock                            _clock;
    DummyWireService                           _metrics_wire_service;
    MemoryConfigStores                         _config_stores;
    vespalib::ThreadStackExecutor              _summary_executor;
    DummyDBOwner                               _document_db_owner;
    BucketSpace                                _bucket_space;
    std::shared_ptr<DocumentDB>                _document_db;
    MyPersistenceEngineOwner                   _persistence_owner;
    MyResourceWriteFilter                      _write_filter;
    test::DiskMemUsageNotifier                 _disk_mem_usage_notifier;
    std::shared_ptr<PersistenceEngine>         _persistence_engine;
    std::unique_ptr<const FieldSetRepo>        _field_set_repo;
    uint32_t                                   _bucket_bits;
    MyServiceLayerConfig                       _service_layer_config;
    MyDistributorConfig                        _distributor_config;
    MyRpcClientConfig                          _rpc_client_config;
    MyMessageBusConfig                         _message_bus_config;
    ConfigSet                                  _config_set;
    std::shared_ptr<IConfigContext>            _config_context;
    std::unique_ptr<IBmFeedHandler>            _feed_handler;
    std::unique_ptr<mbus::Slobrok>             _slobrok;
    std::shared_ptr<BmStorageLinkContext>      _service_layer_chain_context;
    std::unique_ptr<MyServiceLayerProcess>     _service_layer;
    std::unique_ptr<SharedRpcResources>        _rpc_client_shared_rpc_resources;
    std::shared_ptr<BmStorageLinkContext>      _distributor_chain_context;
    std::unique_ptr<storage::DistributorProcess> _distributor;
    std::unique_ptr<BmMessageBus>              _message_bus;

    explicit PersistenceProviderFixture(const BMParams& params);
    ~PersistenceProviderFixture();
    void create_document_db(const BMParams & params);
    uint32_t num_buckets() const { return (1u << _bucket_bits); }
    BucketId make_bucket_id(uint32_t n) const { return BucketId(_bucket_bits, n & (num_buckets() - 1)); }
    document::Bucket make_bucket(uint32_t n) const { return document::Bucket(_bucket_space, make_bucket_id(n)); }
    DocumentId make_document_id(uint32_t n, uint32_t i) const;
    std::unique_ptr<Document> make_document(uint32_t n, uint32_t i) const;
    std::unique_ptr<DocumentUpdate> make_document_update(uint32_t n, uint32_t i) const;
    void create_buckets();
    void wait_slobrok(const vespalib::string &name);
    void start_service_layer(const BMParams& params);
    void start_distributor(const BMParams& params);
    void start_message_bus();
    void create_feed_handler(const BMParams& params);
    void shutdown_feed_handler();
    void shutdown_message_bus();
    void shutdown_distributor();
    void shutdown_service_layer();
};

PersistenceProviderFixture::PersistenceProviderFixture(const BMParams& params)
    : _document_types(make_document_type()),
      _repo(DocumentTypeRepoFactory::make(*_document_types)),
      _doc_type_name("test"),
      _document_type(_repo->getDocumentType(_doc_type_name.getName())),
      _field(_document_type->getField("int")),
      _document_db_config(make_document_db_config(_document_types, _repo, _doc_type_name)),
      _base_dir(base_dir),
      _file_header_context(),
      _tls_listen_port(9017),
      _slobrok_port(9018),
      _rpc_client_port(9019),
      _service_layer_mbus_port(9020),
      _service_layer_rpc_port(9021),
      _service_layer_status_port(9022),
      _distributor_mbus_port(9023),
      _distributor_rpc_port(9024),
      _distributor_status_port(9025),
      _tls("tls", _tls_listen_port, _base_dir, _file_header_context),
      _tls_spec(vespalib::make_string("tcp/localhost:%d", _tls_listen_port)),
      _query_limiter(),
      _clock(),
      _metrics_wire_service(),
      _config_stores(),
      _summary_executor(8, 128_Ki),
      _document_db_owner(),
      _bucket_space(makeBucketSpace(_doc_type_name.getName())),
      _document_db(),
      _persistence_owner(),
      _write_filter(),
      _disk_mem_usage_notifier(),
      _persistence_engine(),
      _field_set_repo(std::make_unique<const FieldSetRepo>(*_repo)),
      _bucket_bits(16),
      _service_layer_config("bm-servicelayer", *_document_types, _slobrok_port, _service_layer_mbus_port, _service_layer_rpc_port, _service_layer_status_port, params),
      _distributor_config("bm-distributor", *_document_types, _slobrok_port, _distributor_mbus_port, _distributor_rpc_port, _distributor_status_port, params),
      _rpc_client_config("bm-rpc-client", _slobrok_port),
      _message_bus_config("bm-message-bus", _slobrok_port),
      _config_set(),
      _config_context(std::make_shared<ConfigContext>(_config_set)),
      _feed_handler(),
      _slobrok(),
      _service_layer_chain_context(),
      _service_layer(),
      _rpc_client_shared_rpc_resources(),
      _distributor_chain_context(),
      _distributor(),
      _message_bus()
{
    create_document_db(params);
    _persistence_engine = std::make_unique<PersistenceEngine>(_persistence_owner, _write_filter, _disk_mem_usage_notifier, -1, false);
    auto proxy = std::make_shared<PersistenceHandlerProxy>(_document_db);
    _persistence_engine->putHandler(_persistence_engine->getWLock(), _bucket_space, _doc_type_name, proxy);
    _service_layer_config.add_builders(_config_set);
    _distributor_config.add_builders(_config_set);
    _rpc_client_config.add_builders(_config_set);
    _message_bus_config.add_builders(_config_set);
    _feed_handler = std::make_unique<SpiBmFeedHandler>(*_persistence_engine, *_field_set_repo, params.get_skip_get_spi_bucket_info());
}

PersistenceProviderFixture::~PersistenceProviderFixture()
{
    if (_persistence_engine) {
        _persistence_engine->destroyIterators();
        _persistence_engine->removeHandler(_persistence_engine->getWLock(), _bucket_space, _doc_type_name);
    }
    if (_document_db) {
        _document_db->close();
    }
}

void
PersistenceProviderFixture::create_document_db(const BMParams & params)
{
    vespalib::mkdir(_base_dir, false);
    vespalib::mkdir(_base_dir + "/" + _doc_type_name.getName(), false);
    vespalib::string input_cfg = _base_dir + "/" + _doc_type_name.getName() + "/baseconfig";
    {
        FileConfigManager fileCfg(input_cfg, "", _doc_type_name.getName());
        fileCfg.saveConfig(*_document_db_config, 1);
    }
    config::DirSpec spec(input_cfg + "/config-1");
    auto tuneFileDocDB = std::make_shared<TuneFileDocumentDB>();
    DocumentDBConfigHelper mgr(spec, _doc_type_name.getName());
    auto protonCfg = std::make_shared<ProtonConfigBuilder>();
    if ( ! params.get_indexing_sequencer().empty()) {
        vespalib::string sequencer = params.get_indexing_sequencer();
        std::transform(sequencer.begin(), sequencer.end(), sequencer.begin(), [](unsigned char c){ return std::toupper(c); });
        protonCfg->indexing.optimize = ProtonConfig::Indexing::getOptimize(sequencer);
    }
    auto bootstrap_config = std::make_shared<BootstrapConfig>(1,
                                                              _document_types,
                                                              _repo,
                                                              std::move(protonCfg),
                                                              std::make_shared<FiledistributorrpcConfig>(),
                                                              std::make_shared<BucketspacesConfig>(),
                                                              tuneFileDocDB, HwInfo());
    mgr.forwardConfig(bootstrap_config);
    mgr.nextGeneration(0ms);
    _document_db = std::make_shared<DocumentDB>(_base_dir,
                                                mgr.getConfig(),
                                                _tls_spec,
                                                _query_limiter,
                                                _clock,
                                                _doc_type_name,
                                                _bucket_space,
                                                *bootstrap_config->getProtonConfigSP(),
                                                _document_db_owner,
                                                _summary_executor,
                                                _summary_executor,
                                                *_persistence_engine,
                                                _tls,
                                                _metrics_wire_service,
                                                _file_header_context,
                                                _config_stores.getConfigStore(_doc_type_name.toString()),
                                                std::make_shared<vespalib::ThreadStackExecutor>(16, 128_Ki),
                                                HwInfo());
    _document_db->start();
    _document_db->waitForOnlineState();
}

DocumentId
PersistenceProviderFixture::make_document_id(uint32_t n, uint32_t i) const
{
    DocumentId id(vespalib::make_string("id::test:n=%u:%u", n & (num_buckets() - 1), i));
    return id;
}

std::unique_ptr<Document>
PersistenceProviderFixture::make_document(uint32_t n, uint32_t i) const
{
    auto id = make_document_id(n, i);
    auto document = std::make_unique<Document>(*_document_type, id);
    document->setRepo(*_repo);
    document->setFieldValue(_field, std::make_unique<IntFieldValue>(i));
    return document;
}

std::unique_ptr<DocumentUpdate>
PersistenceProviderFixture::make_document_update(uint32_t n, uint32_t i) const
{
    auto id = make_document_id(n, i);
    auto document_update = std::make_unique<DocumentUpdate>(*_repo, *_document_type, id);
    document_update->addUpdate(FieldUpdate(_field).addUpdate(AssignValueUpdate(IntFieldValue(15))));
    return document_update;
}

void
PersistenceProviderFixture::create_buckets()
{
    SpiBmFeedHandler feed_handler(*_persistence_engine, *_field_set_repo, false);
    for (unsigned int i = 0; i < num_buckets(); ++i) {
        feed_handler.create_bucket(make_bucket(i));
    }
}

void
PersistenceProviderFixture::wait_slobrok(const vespalib::string &name)
{
    auto &mirror = _rpc_client_shared_rpc_resources->slobrok_mirror();
    LOG(info, "Waiting for %s in slobrok", name.c_str());
    for (;;) {
        auto specs = mirror.lookup(name);
        if (!specs.empty()) {
            LOG(info, "Found %s in slobrok", name.c_str());
            return;
        }
        std::this_thread::sleep_for(100ms);
    }
}

void
PersistenceProviderFixture::start_service_layer(const BMParams& params)
{
    LOG(info, "start slobrok");
    _slobrok = std::make_unique<mbus::Slobrok>(_slobrok_port);
    LOG(info, "start service layer");
    config::ConfigUri config_uri("bm-servicelayer", _config_context);
    std::unique_ptr<BmStorageChainBuilder> chain_builder;
    if (params.get_use_storage_chain() && !params.needs_distributor()) {
        chain_builder = std::make_unique<BmStorageChainBuilder>();
        _service_layer_chain_context = chain_builder->get_context();
    }
    _service_layer = std::make_unique<MyServiceLayerProcess>(config_uri,
                                                             *_persistence_engine,
                                                             std::move(chain_builder));
    _service_layer->setupConfig(100ms);
    _service_layer->createNode();
    _service_layer->getNode().waitUntilInitialized();
    LOG(info, "start rpc client shared resources");
    config::ConfigUri client_config_uri("bm-rpc-client", _config_context);
    _rpc_client_shared_rpc_resources = std::make_unique<SharedRpcResources>
            (client_config_uri, _rpc_client_port, 100, params.get_rpc_events_before_wakup());
    _rpc_client_shared_rpc_resources->start_server_and_register_slobrok("bm-rpc-client");
    wait_slobrok("storage/cluster.storage/storage/0/default");
    wait_slobrok("storage/cluster.storage/storage/0");
    BmClusterController fake_controller(*_rpc_client_shared_rpc_resources);
    fake_controller.set_cluster_up(false);
}

void
PersistenceProviderFixture::start_distributor(const BMParams& params)
{
    config::ConfigUri config_uri("bm-distributor", _config_context);
    std::unique_ptr<BmStorageChainBuilder> chain_builder;
    if (params.get_use_storage_chain() && !params.get_use_document_api()) {
        chain_builder = std::make_unique<BmStorageChainBuilder>();
        _distributor_chain_context = chain_builder->get_context();
    }
    _distributor = std::make_unique<storage::DistributorProcess>(config_uri);
    if (chain_builder) {
        _distributor->set_storage_chain_builder(std::move(chain_builder));
    }
    _distributor->setupConfig(100ms);
    _distributor->createNode();
    wait_slobrok("storage/cluster.storage/distributor/0/default");
    wait_slobrok("storage/cluster.storage/distributor/0");
    BmClusterController fake_controller(*_rpc_client_shared_rpc_resources);
    fake_controller.set_cluster_up(true);
    // Wait for bucket ownership transfer safe time
    std::this_thread::sleep_for(2s);
}

void
PersistenceProviderFixture::start_message_bus()
{
    config::ConfigUri config_uri("bm-message-bus", _config_context);
    LOG(info, "Starting message bus");
    _message_bus = std::make_unique<BmMessageBus>(config_uri, _repo);
    LOG(info, "Started message bus");
}

void
PersistenceProviderFixture::create_feed_handler(const BMParams& params)
{
    StorageApiRpcService::Params rpc_params;
    // This is the same compression config as the default in stor-communicationmanager.def.
    rpc_params.compression_config = CompressionConfig(CompressionConfig::Type::LZ4, 3, 90, 1024);
    rpc_params.num_rpc_targets_per_node = params.get_rpc_targets_per_node();
    if (params.get_use_document_api()) {
        _feed_handler = std::make_unique<DocumentApiMessageBusBmFeedHandler>(*_message_bus);
    } else if (params.get_enable_distributor()) {
        if (params.get_use_storage_chain()) {
            assert(_distributor_chain_context);
            _feed_handler = std::make_unique<StorageApiChainBmFeedHandler>(_distributor_chain_context, true);
        } else if (params.get_use_message_bus()) {
            _feed_handler = std::make_unique<StorageApiMessageBusBmFeedHandler>(*_message_bus, true);
        } else {
            _feed_handler = std::make_unique<StorageApiRpcBmFeedHandler>(*_rpc_client_shared_rpc_resources, _repo, rpc_params, true);
        }
    } else if (params.needs_service_layer()) {
        if (params.get_use_storage_chain()) {
            assert(_service_layer_chain_context);
            _feed_handler = std::make_unique<StorageApiChainBmFeedHandler>(_service_layer_chain_context, false);
        } else if (params.get_use_message_bus()) {
            _feed_handler = std::make_unique<StorageApiMessageBusBmFeedHandler>(*_message_bus, false);
        } else {
            _feed_handler = std::make_unique<StorageApiRpcBmFeedHandler>(*_rpc_client_shared_rpc_resources, _repo, rpc_params, false);
        }
    }
}

void
PersistenceProviderFixture::shutdown_feed_handler()
{
    _feed_handler.reset();
}

void
PersistenceProviderFixture::shutdown_message_bus()
{
    if (_message_bus) {
        LOG(info, "stop message bus");
        _message_bus.reset();
    }
}

void
PersistenceProviderFixture::shutdown_distributor()
{
    if (_distributor) {
        LOG(info, "stop distributor");
        _distributor->getNode().requestShutdown("controlled shutdown");
        _distributor->shutdown();
    }
}

void
PersistenceProviderFixture::shutdown_service_layer()
{
    if (_rpc_client_shared_rpc_resources) {
        LOG(info, "stop rpc client shared resources");
        _rpc_client_shared_rpc_resources->shutdown();
        _rpc_client_shared_rpc_resources.reset();
    }
    if (_service_layer) {
        LOG(info, "stop service layer");
        _service_layer->getNode().requestShutdown("controlled shutdown");
        _service_layer->shutdown();
    }
    if (_slobrok) {
        LOG(info, "stop slobrok");
        _slobrok.reset();
    }
}

vespalib::nbostream
make_put_feed(PersistenceProviderFixture &f, BMRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_put_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << f.make_bucket_id(n);
        auto document = f.make_document(n, i);
        document->serialize(serialized_feed);
    }
    return serialized_feed;
}

std::vector<vespalib::nbostream>
make_feed(vespalib::ThreadStackExecutor &executor, const BMParams &bm_params, std::function<vespalib::nbostream(BMRange,BucketSelector)> func, uint32_t num_buckets, const vespalib::string &label)
{
    LOG(info, "make_feed %s %u small documents", label.c_str(), bm_params.get_documents());
    std::vector<vespalib::nbostream> serialized_feed_v;
    auto start_time = std::chrono::steady_clock::now();
    serialized_feed_v.resize(bm_params.get_client_threads());
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        BucketSelector bucket_selector(i, bm_params.get_client_threads(), num_buckets);
        executor.execute(makeLambdaTask([&serialized_feed_v, i, range, &func, bucket_selector]()
                                        { serialized_feed_v[i] = func(range, bucket_selector); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    LOG(info, "%8.2f %s data elements/s", bm_params.get_documents() / elapsed.count(), label.c_str());
    return serialized_feed_v;
}

void
put_async_task(PersistenceProviderFixture &f, uint32_t max_pending, BMRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "put_async_task([%u..%u))", range.get_start(), range.get_end());
    feedbm::PendingTracker pending_tracker(max_pending);
    f._feed_handler->attach_bucket_info_queue(pending_tracker);
    auto &repo = *f._repo;
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    BucketId bucket_id;
    auto bucket_space = f._bucket_space;
    bool use_timestamp = !f._feed_handler->manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(bucket_space, bucket_id);
        auto document = std::make_unique<Document>(repo, is);
        f._feed_handler->put(bucket, std::move(document), (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

class AvgSampler {
private:
    double _total;
    size_t _samples;

public:
    AvgSampler() : _total(0), _samples(0) {}
    void sample(double val) {
        _total += val;
        ++_samples;
    }
    double avg() const { return _total / (double)_samples; }
};

void
run_put_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass, int64_t& time_bias,
                    const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    uint32_t old_errors = f._feed_handler->get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { put_async_task(f, max_pending, range, serialized_feed, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = f._feed_handler->get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "putAsync: pass=%u, errors=%u, puts/s: %8.2f", pass, new_errors, throughput);
    time_bias += bm_params.get_documents();
}

vespalib::nbostream
make_update_feed(PersistenceProviderFixture &f, BMRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_update_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << f.make_bucket_id(n);
        auto document_update = f.make_document_update(n, i);
        document_update->serializeHEAD(serialized_feed);
    }
    return serialized_feed;
}

void
update_async_task(PersistenceProviderFixture &f, uint32_t max_pending, BMRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "update_async_task([%u..%u))", range.get_start(), range.get_end());
    feedbm::PendingTracker pending_tracker(max_pending);
    f._feed_handler->attach_bucket_info_queue(pending_tracker);
    auto &repo = *f._repo;
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    BucketId bucket_id;
    auto bucket_space = f._bucket_space;
    bool use_timestamp = !f._feed_handler->manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(bucket_space, bucket_id);
        auto document_update = DocumentUpdate::createHEAD(repo, is);
        f._feed_handler->update(bucket, std::move(document_update), (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
run_update_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass, int64_t& time_bias,
                       const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    uint32_t old_errors = f._feed_handler->get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { update_async_task(f, max_pending, range, serialized_feed, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = f._feed_handler->get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "updateAsync: pass=%u, errors=%u, updates/s: %8.2f", pass, new_errors, throughput);
    time_bias += bm_params.get_documents();
}

void
get_async_task(PersistenceProviderFixture &f, uint32_t max_pending, BMRange range, const vespalib::nbostream &serialized_feed)
{
    LOG(debug, "get_async_task([%u..%u))", range.get_start(), range.get_end());
    feedbm::PendingTracker pending_tracker(max_pending);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    BucketId bucket_id;
    vespalib::string all_fields(document::AllFields::NAME);
    auto bucket_space = f._bucket_space;
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(bucket_space, bucket_id);
        DocumentId document_id(is);
        f._feed_handler->get(bucket, all_fields, document_id, pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
run_get_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass,
                       const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    uint32_t old_errors = f._feed_handler->get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range]()
                                        { get_async_task(f, max_pending, range, serialized_feed); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = f._feed_handler->get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "getAsync: pass=%u, errors=%u, gets/s: %8.2f", pass, new_errors, throughput);
}

vespalib::nbostream
make_remove_feed(PersistenceProviderFixture &f, BMRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_update_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << f.make_bucket_id(n);
        auto document_id = f.make_document_id(n, i);
        vespalib::string raw_id = document_id.toString();
        serialized_feed.write(raw_id.c_str(), raw_id.size() + 1);
    }
    return serialized_feed;
}

void
remove_async_task(PersistenceProviderFixture &f, uint32_t max_pending, BMRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "remove_async_task([%u..%u))", range.get_start(), range.get_end());
    feedbm::PendingTracker pending_tracker(max_pending);
    f._feed_handler->attach_bucket_info_queue(pending_tracker);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    BucketId bucket_id;
    auto bucket_space = f._bucket_space;
    bool use_timestamp = !f._feed_handler->manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(bucket_space, bucket_id);
        DocumentId document_id(is);
        f._feed_handler->remove(bucket, document_id, (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
run_remove_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass, int64_t& time_bias,
                       const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    uint32_t old_errors = f._feed_handler->get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { remove_async_task(f, max_pending, range, serialized_feed, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = f._feed_handler->get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "removeAsync: pass=%u, errors=%u, removes/s: %8.2f", pass, new_errors, throughput);
    time_bias += bm_params.get_documents();
}

void
benchmark_async_put(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor,
                    int64_t& time_bias, const std::vector<vespalib::nbostream>& feed, const BMParams& params)
{
    AvgSampler sampler;
    LOG(info, "--------------------------------");
    LOG(info, "putAsync: %u small documents, passes=%u", params.get_documents(), params.get_put_passes());
    for (uint32_t pass = 0; pass < params.get_put_passes(); ++pass) {
        run_put_async_tasks(f, executor, pass, time_bias, feed, params, sampler);
    }
    LOG(info, "putAsync: AVG puts/s: %8.2f", sampler.avg());
}

void
benchmark_async_update(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor,
                       int64_t& time_bias, const std::vector<vespalib::nbostream>& feed, const BMParams& params)
{
    if (params.get_update_passes() == 0) {
        return;
    }
    AvgSampler sampler;
    LOG(info, "--------------------------------");
    LOG(info, "updateAsync: %u small documents, passes=%u", params.get_documents(), params.get_update_passes());
    for (uint32_t pass = 0; pass < params.get_update_passes(); ++pass) {
        run_update_async_tasks(f, executor, pass, time_bias, feed, params, sampler);
    }
    LOG(info, "updateAsync: AVG updates/s: %8.2f", sampler.avg());
}

void
benchmark_async_get(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor,
                    const std::vector<vespalib::nbostream>& feed, const BMParams& params)
{
    if (params.get_get_passes() == 0) {
        return;
    }
    LOG(info, "--------------------------------");
    LOG(info, "getAsync: %u small documents, passes=%u", params.get_documents(), params.get_get_passes());
    AvgSampler sampler;
    for (uint32_t pass = 0; pass < params.get_get_passes(); ++pass) {
        run_get_async_tasks(f, executor, pass, feed, params, sampler);
    }
    LOG(info, "getAsync: AVG gets/s: %8.2f", sampler.avg());
}

void
benchmark_async_remove(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor,
                       int64_t& time_bias, const std::vector<vespalib::nbostream>& feed, const BMParams& params)
{
    if (params.get_remove_passes() == 0) {
        return;
    }
    LOG(info, "--------------------------------");
    LOG(info, "removeAsync: %u small documents, passes=%u", params.get_documents(), params.get_remove_passes());
    AvgSampler sampler;
    for (uint32_t pass = 0; pass < params.get_remove_passes(); ++pass) {
        run_remove_async_tasks(f, executor, pass, time_bias, feed, params, sampler);
    }
    LOG(info, "removeAsync: AVG removes/s: %8.2f", sampler.avg());
}

void benchmark_async_spi(const BMParams &bm_params)
{
    vespalib::rmdir(base_dir, true);
    PersistenceProviderFixture f(bm_params);
    auto &provider = *f._persistence_engine;
    LOG(info, "start initialize");
    provider.initialize();
    LOG(info, "create %u buckets", f.num_buckets());
    if (!bm_params.needs_distributor()) {
        f.create_buckets();
    }
    if (bm_params.needs_service_layer()) {
        f.start_service_layer(bm_params);
    }
    if (bm_params.needs_distributor()) {
        f.start_distributor(bm_params);
    }
    if (bm_params.needs_message_bus()) {
        f.start_message_bus();
    }
    f.create_feed_handler(bm_params);
    vespalib::ThreadStackExecutor executor(bm_params.get_client_threads(), 128_Ki);
    auto put_feed = make_feed(executor, bm_params, [&f](BMRange range, BucketSelector bucket_selector) { return make_put_feed(f, range, bucket_selector); }, f.num_buckets(), "put");
    auto update_feed = make_feed(executor, bm_params, [&f](BMRange range, BucketSelector bucket_selector) { return make_update_feed(f, range, bucket_selector); }, f.num_buckets(), "update");
    auto remove_feed = make_feed(executor, bm_params, [&f](BMRange range, BucketSelector bucket_selector) { return make_remove_feed(f, range, bucket_selector); }, f.num_buckets(), "remove");
    int64_t time_bias = 1;
    LOG(info, "Feed handler is '%s'", f._feed_handler->get_name().c_str());
    benchmark_async_put(f, executor, time_bias, put_feed, bm_params);
    benchmark_async_update(f, executor, time_bias, update_feed, bm_params);
    benchmark_async_get(f, executor, remove_feed, bm_params);
    benchmark_async_remove(f, executor, time_bias, remove_feed, bm_params);
    LOG(info, "--------------------------------");

    f.shutdown_feed_handler();
    f.shutdown_message_bus();
    f.shutdown_distributor();
    f.shutdown_service_layer();
}

class App : public FastOS_Application
{
    BMParams _bm_params;
public:
    App();
    ~App() override;
    void usage();
    bool get_options();
    int Main() override;
};

App::App()
    : _bm_params()
{
}

App::~App() = default;

void
App::usage()
{
    std::cerr <<
        "vespa-feed-bm version 0.0\n"
        "\n"
        "USAGE:\n";
    std::cerr <<
        "vespa-feed-bm\n"
        "[--bucket-db-stripe-bits]\n"
        "[--client-threads threads]\n"
        "[--get-passes get-passes]\n"
        "[--indexing-sequencer [latency,throughput,adaptive]]\n"
        "[--max-pending max-pending]\n"
        "[--documents documents]\n"
        "[--put-passes put-passes]\n"
        "[--update-passes update-passes]\n"
        "[--remove-passes remove-passes]\n"
        "[--rpc-events-before-wakeup events]\n"
        "[--rpc-network-threads threads]\n"
        "[--rpc-targets-per-node targets]\n"
        "[--response-threads threads]\n"
        "[--enable-distributor]\n"
        "[--enable-service-layer]\n"
        "[--skip-get-spi-bucket-info]\n"
        "[--use-document-api]\n"
        "[--use-async-message-handling]\n"
        "[--use-message-bus\n"
        "[--use-storage-chain]" << std::endl;
}

bool
App::get_options()
{
    int c;
    const char *opt_argument = nullptr;
    int long_opt_index = 0;
    static struct option long_opts[] = {
        { "bucket-db-stripe-bits", 1, nullptr, 0 },
        { "client-threads", 1, nullptr, 0 },
        { "documents", 1, nullptr, 0 },
        { "enable-distributor", 0, nullptr, 0 },
        { "enable-service-layer", 0, nullptr, 0 },
        { "get-passes", 1, nullptr, 0 },
        { "indexing-sequencer", 1, nullptr, 0 },
        { "max-pending", 1, nullptr, 0 },
        { "put-passes", 1, nullptr, 0 },
        { "remove-passes", 1, nullptr, 0 },
        { "response-threads", 1, nullptr, 0 },
        { "rpc-events-before-wakeup", 1, nullptr, 0 },
        { "rpc-network-threads", 1, nullptr, 0 },
        { "rpc-targets-per-node", 1, nullptr, 0 },
        { "skip-get-spi-bucket-info", 0, nullptr, 0 },
        { "update-passes", 1, nullptr, 0 },
        { "use-async-message-handling", 0, nullptr, 0 },
        { "use-document-api", 0, nullptr, 0 },
        { "use-message-bus", 0, nullptr, 0 },
        { "use-storage-chain", 0, nullptr, 0 }
    };
    enum longopts_enum {
        LONGOPT_BUCKET_DB_STRIPE_BITS,
        LONGOPT_CLIENT_THREADS,
        LONGOPT_DOCUMENTS,
        LONGOPT_ENABLE_DISTRIBUTOR,
        LONGOPT_ENABLE_SERVICE_LAYER,
        LONGOPT_GET_PASSES,
        LONGOPT_INDEXING_SEQUENCER,
        LONGOPT_MAX_PENDING,
        LONGOPT_PUT_PASSES,
        LONGOPT_REMOVE_PASSES,
        LONGOPT_RESPONSE_THREADS,
        LONGOPT_RPC_EVENTS_BEFORE_WAKEUP,
        LONGOPT_RPC_NETWORK_THREADS,
        LONGOPT_RPC_TARGETS_PER_NODE,
        LONGOPT_SKIP_GET_SPI_BUCKET_INFO,
        LONGOPT_UPDATE_PASSES,
        LONGOPT_USE_ASYNC_MESSAGE_HANDLING,
        LONGOPT_USE_DOCUMENT_API,
        LONGOPT_USE_MESSAGE_BUS,
        LONGOPT_USE_STORAGE_CHAIN
    };
    int opt_index = 1;
    resetOptIndex(opt_index);
    while ((c = GetOptLong("", opt_argument, opt_index, long_opts, &long_opt_index)) != -1) {
        switch (c) {
        case 0:
            switch(long_opt_index) {
            case LONGOPT_BUCKET_DB_STRIPE_BITS:
                _bm_params.set_bucket_db_stripe_bits(atoi(opt_argument));
                break;
            case LONGOPT_CLIENT_THREADS:
                _bm_params.set_client_threads(atoi(opt_argument));
                break;
            case LONGOPT_DOCUMENTS:
                _bm_params.set_documents(atoi(opt_argument));
                break;
            case LONGOPT_ENABLE_DISTRIBUTOR:
                _bm_params.set_enable_distributor(true);
                break;
            case LONGOPT_ENABLE_SERVICE_LAYER:
                _bm_params.set_enable_service_layer(true);
                break;
            case LONGOPT_GET_PASSES:
                _bm_params.set_get_passes(atoi(opt_argument));
                break;
            case LONGOPT_INDEXING_SEQUENCER:
                _bm_params.set_indexing_sequencer(opt_argument);
                break;
            case LONGOPT_MAX_PENDING:
                _bm_params.set_max_pending(atoi(opt_argument));
                break;
            case LONGOPT_PUT_PASSES:
                _bm_params.set_put_passes(atoi(opt_argument));
                break;
            case LONGOPT_UPDATE_PASSES:
                _bm_params.set_update_passes(atoi(opt_argument));
                break;
            case LONGOPT_REMOVE_PASSES:
                _bm_params.set_remove_passes(atoi(opt_argument));
                break;
            case LONGOPT_RESPONSE_THREADS:
                _bm_params.set_response_threads(atoi(opt_argument));
                break;
            case LONGOPT_RPC_EVENTS_BEFORE_WAKEUP:
                _bm_params.set_rpc_events_before_wakeup(atoi(opt_argument));
                break;
            case LONGOPT_RPC_NETWORK_THREADS:
                _bm_params.set_rpc_network_threads(atoi(opt_argument));
                break;
            case LONGOPT_RPC_TARGETS_PER_NODE:
                _bm_params.set_rpc_targets_per_node(atoi(opt_argument));
                break;
            case LONGOPT_SKIP_GET_SPI_BUCKET_INFO:
                _bm_params.set_skip_get_spi_bucket_info(true);
                break;
            case LONGOPT_USE_ASYNC_MESSAGE_HANDLING:
                _bm_params.set_use_async_message_handling_on_schedule(true);
                break;
            case LONGOPT_USE_DOCUMENT_API:
                _bm_params.set_use_document_api(true);
                break;
            case LONGOPT_USE_MESSAGE_BUS:
                _bm_params.set_use_message_bus(true);
                break;
            case LONGOPT_USE_STORAGE_CHAIN:
                _bm_params.set_use_storage_chain(true);
                break;
            default:
                return false;
            }
            break;
        default:
            return false;
        }
    }
    return _bm_params.check();
}

int
App::Main()
{
    if (!get_options()) {
        usage();
        return 1;
    }
    benchmark_async_spi(_bm_params);
    return 0;
}

int
main(int argc, char* argv[])
{
    DummyFileHeaderContext::setCreator("vespa-feed-bm");
    App app;
    auto exit_value = app.Entry(argc, argv);
    vespalib::rmdir(base_dir, true);
    return exit_value;
}
