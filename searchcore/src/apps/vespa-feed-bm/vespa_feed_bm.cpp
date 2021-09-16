// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fastos/app.h>
#include <vespa/searchcore/bmcluster/avg_sampler.h>
#include <vespa/searchcore/bmcluster/bm_cluster.h>
#include <vespa/searchcore/bmcluster/bm_cluster_controller.h>
#include <vespa/searchcore/bmcluster/bm_cluster_params.h>
#include <vespa/searchcore/bmcluster/bm_feed.h>
#include <vespa/searchcore/bmcluster/bm_feeder.h>
#include <vespa/searchcore/bmcluster/bm_feed_params.h>
#include <vespa/searchcore/bmcluster/bm_node.h>
#include <vespa/searchcore/bmcluster/bm_range.h>
#include <vespa/searchcore/bmcluster/bucket_selector.h>
#include <vespa/searchcore/bmcluster/spi_bm_feed_handler.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <getopt.h>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("vespa-feed-bm");

using namespace proton;
using namespace std::chrono_literals;

using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using document::DocumenttypesConfig;
using document::DocumenttypesConfigBuilder;
using search::bmcluster::AvgSampler;
using search::bmcluster::BmClusterController;
using search::bmcluster::IBmFeedHandler;
using search::bmcluster::BmClusterParams;
using search::bmcluster::BmCluster;
using search::bmcluster::BmFeed;
using search::bmcluster::BmFeedParams;
using search::bmcluster::BmFeeder;
using search::bmcluster::BmNode;
using search::bmcluster::BmRange;
using search::bmcluster::BucketSelector;
using search::index::DummyFileHeaderContext;
using storage::spi::PersistenceProvider;
using vespalib::makeLambdaTask;

namespace {

vespalib::string base_dir = "testdb";
constexpr int base_port = 9017;

std::shared_ptr<DocumenttypesConfig> make_document_types() {
    using Struct = document::config_builder::Struct;
    using DataType = document::DataType;
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "test", Struct("test.header").addField("int", DataType::T_INT), Struct("test.body"));
    return std::make_shared<DocumenttypesConfig>(builder.config());
}

class BMParams : public BmClusterParams,
                 public BmFeedParams
{
    uint32_t _get_passes;
    uint32_t _put_passes;
    uint32_t _update_passes;
    uint32_t _remove_passes;
public:
    BMParams()
        : BmClusterParams(),
          BmFeedParams(),
          _get_passes(0),
          _put_passes(2),
          _update_passes(1),
          _remove_passes(2)
    {
    }
    uint32_t get_get_passes() const { return _get_passes; }
    uint32_t get_put_passes() const { return _put_passes; }
    uint32_t get_update_passes() const { return _update_passes; }
    uint32_t get_remove_passes() const { return _remove_passes; }
    void set_get_passes(uint32_t get_passes_in) { _get_passes = get_passes_in; }
    void set_put_passes(uint32_t put_passes_in) { _put_passes = put_passes_in; }
    void set_update_passes(uint32_t update_passes_in) { _update_passes = update_passes_in; }
    void set_remove_passes(uint32_t remove_passes_in) { _remove_passes = remove_passes_in; }
    bool check() const;
};

bool
BMParams::check() const
{
    if (!BmClusterParams::check()) {
        return false;
    }
    if (!BmFeedParams::check()) {
        return false;
    }
    if (_put_passes < 1) {
        std::cerr << "Put passes too low: " << _put_passes << std::endl;
        return false;
    }

    return true;
}

}

struct PersistenceProviderFixture {
    std::shared_ptr<const DocumenttypesConfig> _document_types;
    std::shared_ptr<const DocumentTypeRepo>    _repo;
    std::unique_ptr<BmCluster>                 _bm_cluster;
    BmFeed                                     _feed;

    explicit PersistenceProviderFixture(const BMParams& params);
    ~PersistenceProviderFixture();
};

PersistenceProviderFixture::PersistenceProviderFixture(const BMParams& params)
    : _document_types(make_document_types()),
      _repo(document::DocumentTypeRepoFactory::make(*_document_types)),
      _bm_cluster(std::make_unique<BmCluster>(base_dir, base_port, params, _document_types, _repo)),
      _feed(_repo)
{
    _bm_cluster->make_nodes();
}

PersistenceProviderFixture::~PersistenceProviderFixture() = default;

void
benchmark_feed(BmFeeder& feeder, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed, const BMParams& params, uint32_t passes, const vespalib::string &op_name)
{
    if (passes == 0) {
        return;
    }
    AvgSampler sampler;
    LOG(info, "--------------------------------");
    LOG(info, "%sAsync: %u small documents, passes=%u", op_name.c_str(), params.get_documents(), passes);
    for (uint32_t pass = 0; pass < passes; ++pass) {
        feeder.run_feed_tasks(pass, time_bias, serialized_feed, params, sampler, op_name);
    }
    LOG(info, "%sAsync: AVG %s/s: %8.2f", op_name.c_str(), op_name.c_str(), sampler.avg());
}

void benchmark(const BMParams &bm_params)
{
    vespalib::rmdir(base_dir, true);
    PersistenceProviderFixture f(bm_params);
    auto& cluster = *f._bm_cluster;
    cluster.start(f._feed);
    vespalib::ThreadStackExecutor executor(bm_params.get_client_threads(), 128_Ki);
    BmFeeder feeder(f._repo, *cluster.get_feed_handler(), executor);
    auto& feed = f._feed;
    auto put_feed = feed.make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_put_feed(range, bucket_selector); }, f._feed.num_buckets(), "put");
    auto update_feed = feed.make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_update_feed(range, bucket_selector); }, f._feed.num_buckets(), "update");
    auto get_feed = feed.make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_get_feed(range, bucket_selector); }, f._feed.num_buckets(), "get");
    auto remove_feed = feed.make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_remove_feed(range, bucket_selector); }, f._feed.num_buckets(), "remove");
    int64_t time_bias = 1;
    LOG(info, "Feed handler is '%s'", feeder.get_feed_handler().get_name().c_str());
    benchmark_feed(feeder, time_bias, put_feed, bm_params, bm_params.get_put_passes(), "put");
    benchmark_feed(feeder, time_bias, update_feed, bm_params, bm_params.get_update_passes(), "update");
    benchmark_feed(feeder, time_bias, get_feed, bm_params, bm_params.get_get_passes(), "get");
    benchmark_feed(feeder, time_bias, remove_feed, bm_params, bm_params.get_remove_passes(), "remove");
    LOG(info, "--------------------------------");

    cluster.stop();
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
        "[--bucket-db-stripe-bits bits]\n"
        "[--client-threads threads]\n"
        "[--distributor-stripes stripes]\n"
        "[--get-passes get-passes]\n"
        "[--indexing-sequencer [latency,throughput,adaptive]]\n"
        "[--max-pending max-pending]\n"
        "[--documents documents]\n"
        "[--nodes nodes]\n"
        "[--put-passes put-passes]\n"
        "[--update-passes update-passes]\n"
        "[--remove-passes remove-passes]\n"
        "[--rpc-events-before-wakeup events]\n"
        "[--rpc-network-threads threads]\n"
        "[--rpc-targets-per-node targets]\n"
        "[--response-threads threads]\n"
        "[--enable-distributor]\n"
        "[--enable-service-layer]\n"
        "[--skip-communicationmanager-thread]\n"
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
        { "distributor-stripes", 1, nullptr, 0 },
        { "documents", 1, nullptr, 0 },
        { "enable-distributor", 0, nullptr, 0 },
        { "enable-service-layer", 0, nullptr, 0 },
        { "get-passes", 1, nullptr, 0 },
        { "indexing-sequencer", 1, nullptr, 0 },
        { "max-pending", 1, nullptr, 0 },
        { "nodes", 1, nullptr, 0 },
        { "put-passes", 1, nullptr, 0 },
        { "remove-passes", 1, nullptr, 0 },
        { "response-threads", 1, nullptr, 0 },
        { "rpc-events-before-wakeup", 1, nullptr, 0 },
        { "rpc-network-threads", 1, nullptr, 0 },
        { "rpc-targets-per-node", 1, nullptr, 0 },
        { "skip-communicationmanager-thread", 0, nullptr, 0 },
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
        LONGOPT_DISTRIBUTOR_STRIPES,
        LONGOPT_DOCUMENTS,
        LONGOPT_ENABLE_DISTRIBUTOR,
        LONGOPT_ENABLE_SERVICE_LAYER,
        LONGOPT_GET_PASSES,
        LONGOPT_INDEXING_SEQUENCER,
        LONGOPT_MAX_PENDING,
        LONGOPT_NODES,
        LONGOPT_PUT_PASSES,
        LONGOPT_REMOVE_PASSES,
        LONGOPT_RESPONSE_THREADS,
        LONGOPT_RPC_EVENTS_BEFORE_WAKEUP,
        LONGOPT_RPC_NETWORK_THREADS,
        LONGOPT_RPC_TARGETS_PER_NODE,
        LONGOPT_SKIP_COMMUNICATIONMANAGER_THREAD,
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
            case LONGOPT_DISTRIBUTOR_STRIPES:
                _bm_params.set_distributor_stripes(atoi(opt_argument));
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
            case LONGOPT_NODES:
                _bm_params.set_num_nodes(atoi(opt_argument));
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
            case LONGOPT_SKIP_COMMUNICATIONMANAGER_THREAD:
                _bm_params.set_skip_communicationmanager_thread(true);
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
    benchmark(_bm_params);
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
