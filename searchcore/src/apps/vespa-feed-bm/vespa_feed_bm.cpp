// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/searchcore/bmcluster/avg_sampler.h>
#include <vespa/searchcore/bmcluster/bm_cluster.h>
#include <vespa/searchcore/bmcluster/bm_cluster_controller.h>
#include <vespa/searchcore/bmcluster/bm_cluster_params.h>
#include <vespa/searchcore/bmcluster/bm_feed.h>
#include <vespa/searchcore/bmcluster/bm_feeder.h>
#include <vespa/searchcore/bmcluster/bm_feed_params.h>
#include <vespa/searchcore/bmcluster/bm_node.h>
#include <vespa/searchcore/bmcluster/bm_node_stats.h>
#include <vespa/searchcore/bmcluster/bm_node_stats_reporter.h>
#include <vespa/searchcore/bmcluster/bm_range.h>
#include <vespa/searchcore/bmcluster/bucket_selector.h>
#include <vespa/searchcore/bmcluster/spi_bm_feed_handler.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <getopt.h>
#include <filesystem>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("vespa-feed-bm");

using namespace proton;
using namespace std::chrono_literals;

using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using search::bmcluster::AvgSampler;
using search::bmcluster::BmClusterController;
using search::bmcluster::IBmFeedHandler;
using search::bmcluster::BmClusterParams;
using search::bmcluster::BmCluster;
using search::bmcluster::BmFeed;
using search::bmcluster::BmFeedParams;
using search::bmcluster::BmFeeder;
using search::bmcluster::BmNode;
using search::bmcluster::BmNodeStatsReporter;
using search::bmcluster::BmRange;
using search::bmcluster::BucketSelector;
using search::index::DummyFileHeaderContext;

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
    if (get_groups() > 0 && !needs_distributor()) {
        std::cerr << "grouped distribution only allowed when using distributor" << std::endl;
        return false;
    }

    return true;
}

}

class Benchmark {
    BMParams                                   _params;
    std::shared_ptr<const DocumenttypesConfig> _document_types;
    std::shared_ptr<const DocumentTypeRepo>    _repo;
    std::unique_ptr<BmCluster>                 _cluster;
    BmFeed                                     _feed;

    void benchmark_feed(BmFeeder& feeder, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed, uint32_t passes, const vespalib::string &op_name);
public:
    explicit Benchmark(const BMParams& params);
    ~Benchmark();
    void run();
};

Benchmark::Benchmark(const BMParams& params)
    : _params(params),
      _document_types(make_document_types()),
      _repo(document::DocumentTypeRepoFactory::make(*_document_types)),
      _cluster(std::make_unique<BmCluster>(base_dir, base_port, _params, _document_types, _repo)),
      _feed(_repo)
{
    _cluster->make_nodes();
}

Benchmark::~Benchmark() = default;

void
Benchmark::benchmark_feed(BmFeeder& feeder, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed, uint32_t passes, const vespalib::string &op_name)
{
    if (passes == 0) {
        return;
    }
    AvgSampler sampler;
    LOG(info, "--------------------------------");
    LOG(info, "%sAsync: %u small documents, passes=%u", op_name.c_str(), _params.get_documents(), passes);
    for (uint32_t pass = 0; pass < passes; ++pass) {
        feeder.run_feed_tasks(pass, time_bias, serialized_feed, _params, sampler, op_name);
    }
    LOG(info, "%sAsync: AVG %s/s: %8.2f", op_name.c_str(), op_name.c_str(), sampler.avg());
}

void
Benchmark::run()
{
    _cluster->start(_feed);
    vespalib::ThreadStackExecutor executor(_params.get_client_threads(), 128_Ki);
    BmFeeder feeder(_repo, *_cluster->get_feed_handler(), executor);
    auto put_feed = _feed.make_feed(executor, _params, [this](BmRange range, BucketSelector bucket_selector) { return _feed.make_put_feed(range, bucket_selector); }, _feed.num_buckets(), "put");
    auto update_feed = _feed.make_feed(executor, _params, [this](BmRange range, BucketSelector bucket_selector) { return _feed.make_update_feed(range, bucket_selector); }, _feed.num_buckets(), "update");
    auto get_feed = _feed.make_feed(executor, _params, [this](BmRange range, BucketSelector bucket_selector) { return _feed.make_get_feed(range, bucket_selector); }, _feed.num_buckets(), "get");
    auto remove_feed = _feed.make_feed(executor, _params, [this](BmRange range, BucketSelector bucket_selector) { return _feed.make_remove_feed(range, bucket_selector); }, _feed.num_buckets(), "remove");
    BmNodeStatsReporter reporter(*_cluster, false);
    reporter.start(500ms);
    int64_t time_bias = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch() - 24h).count();
    LOG(info, "Feed handler is '%s'", feeder.get_feed_handler().get_name().c_str());
    benchmark_feed(feeder, time_bias, put_feed, _params.get_put_passes(), "put");
    reporter.report_now();
    benchmark_feed(feeder, time_bias, update_feed, _params.get_update_passes(), "update");
    reporter.report_now();
    benchmark_feed(feeder, time_bias, get_feed, _params.get_get_passes(), "get");
    reporter.report_now();
    benchmark_feed(feeder, time_bias, remove_feed, _params.get_remove_passes(), "remove");
    reporter.report_now();
    reporter.stop();
    LOG(info, "--------------------------------");

    _cluster->stop();
}

class App
{
    BMParams _bm_params;
public:
    App();
    ~App();
    void usage();
    bool get_options(int argc, char **argv);
    int main(int argc, char **argv);
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
        "[--documents documents]\n"
        "[--enable-distributor]\n"
        "[--enable-service-layer]\n"
        "[--get-passes get-passes]\n"
        "[--groups groups]\n"
        "[--indexing-sequencer [latency,throughput,adaptive]]\n"
        "[--max-pending max-pending]\n"
        "[--nodes-per-group nodes-per-group]\n"
        "[--put-passes put-passes]\n"
        "[--remove-passes remove-passes]\n"
        "[--response-threads threads]\n"
        "[--rpc-events-before-wakeup events]\n"
        "[--rpc-network-threads threads]\n"
        "[--rpc-targets-per-node targets]\n"
        "[--skip-get-spi-bucket-info]\n"
        "[--update-passes update-passes]\n"
        "[--use-async-message-handling]\n"
        "[--use-document-api]\n"
        "[--use-message-bus\n"
        "[--use-storage-chain]" << std::endl;
}

bool
App::get_options(int argc, char **argv)
{
    int c;
    int long_opt_index = 0;
    static struct option long_opts[] = {
        { "bucket-db-stripe-bits", 1, nullptr, 0 },
        { "client-threads", 1, nullptr, 0 },
        { "distributor-stripes", 1, nullptr, 0 },
        { "documents", 1, nullptr, 0 },
        { "enable-distributor", 0, nullptr, 0 },
        { "enable-service-layer", 0, nullptr, 0 },
        { "get-passes", 1, nullptr, 0 },
        { "groups", 1, nullptr, 0 },
        { "indexing-sequencer", 1, nullptr, 0 },
        { "max-pending", 1, nullptr, 0 },
        { "nodes-per-group", 1, nullptr, 0 },
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
        { "use-storage-chain", 0, nullptr, 0 },
        { nullptr, 0, nullptr, 0 }
    };
    enum longopts_enum {
        LONGOPT_BUCKET_DB_STRIPE_BITS,
        LONGOPT_CLIENT_THREADS,
        LONGOPT_DISTRIBUTOR_STRIPES,
        LONGOPT_DOCUMENTS,
        LONGOPT_ENABLE_DISTRIBUTOR,
        LONGOPT_ENABLE_SERVICE_LAYER,
        LONGOPT_GET_PASSES,
        LONGOPT_GROUPS,
        LONGOPT_INDEXING_SEQUENCER,
        LONGOPT_MAX_PENDING,
        LONGOPT_NODES_PER_GROUP,
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
    optind = 1;
    while ((c = getopt_long(argc, argv, "", long_opts, &long_opt_index)) != -1) {
        switch (c) {
        case 0:
            switch(long_opt_index) {
            case LONGOPT_BUCKET_DB_STRIPE_BITS:
                _bm_params.set_bucket_db_stripe_bits(atoi(optarg));
                break;
            case LONGOPT_CLIENT_THREADS:
                _bm_params.set_client_threads(atoi(optarg));
                break;
            case LONGOPT_DISTRIBUTOR_STRIPES:
                _bm_params.set_distributor_stripes(atoi(optarg));
                break;
            case LONGOPT_DOCUMENTS:
                _bm_params.set_documents(atoi(optarg));
                break;
            case LONGOPT_ENABLE_DISTRIBUTOR:
                _bm_params.set_enable_distributor(true);
                break;
            case LONGOPT_ENABLE_SERVICE_LAYER:
                _bm_params.set_enable_service_layer(true);
                break;
            case LONGOPT_GET_PASSES:
                _bm_params.set_get_passes(atoi(optarg));
                break;
            case LONGOPT_GROUPS:
                _bm_params.set_groups(atoi(optarg));
                break;
            case LONGOPT_INDEXING_SEQUENCER:
                _bm_params.set_indexing_sequencer(optarg);
                break;
            case LONGOPT_MAX_PENDING:
                _bm_params.set_max_pending(atoi(optarg));
                break;
            case LONGOPT_NODES_PER_GROUP:
                _bm_params.set_nodes_per_group(atoi(optarg));
                break;
            case LONGOPT_PUT_PASSES:
                _bm_params.set_put_passes(atoi(optarg));
                break;
            case LONGOPT_UPDATE_PASSES:
                _bm_params.set_update_passes(atoi(optarg));
                break;
            case LONGOPT_REMOVE_PASSES:
                _bm_params.set_remove_passes(atoi(optarg));
                break;
            case LONGOPT_RESPONSE_THREADS:
                _bm_params.set_response_threads(atoi(optarg));
                break;
            case LONGOPT_RPC_EVENTS_BEFORE_WAKEUP:
                _bm_params.set_rpc_events_before_wakeup(atoi(optarg));
                break;
            case LONGOPT_RPC_NETWORK_THREADS:
                _bm_params.set_rpc_network_threads(atoi(optarg));
                break;
            case LONGOPT_RPC_TARGETS_PER_NODE:
                _bm_params.set_rpc_targets_per_node(atoi(optarg));
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
App::main(int argc, char **argv)
{
    if (!get_options(argc, argv)) {
        usage();
        return 1;
    }
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    Benchmark bm(_bm_params);
    bm.run();
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    DummyFileHeaderContext::setCreator("vespa-feed-bm");
    App app;
    auto exit_value = app.main(argc, argv);
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    return exit_value;
}
