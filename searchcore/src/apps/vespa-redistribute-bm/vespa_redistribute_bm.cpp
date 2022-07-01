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
#include <vespa/searchcore/bmcluster/bm_distribution.h>
#include <vespa/searchcore/bmcluster/bm_feed.h>
#include <vespa/searchcore/bmcluster/bm_feeder.h>
#include <vespa/searchcore/bmcluster/bm_feed_params.h>
#include <vespa/searchcore/bmcluster/bm_node.h>
#include <vespa/searchcore/bmcluster/bm_node_stats.h>
#include <vespa/searchcore/bmcluster/bm_node_stats_reporter.h>
#include <vespa/searchcore/bmcluster/bm_range.h>
#include <vespa/searchcore/bmcluster/bucket_db_snapshot_vector.h>
#include <vespa/searchcore/bmcluster/bucket_selector.h>
#include <vespa/searchcore/bmcluster/spi_bm_feed_handler.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <getopt.h>
#include <filesystem>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("vespa-redistribute-bm");

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
using storage::lib::State;
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

enum class Mode {
    GROW,
    SHRINK,
    PERM_CRASH,
    TEMP_CRASH,
    REPLACE,
    BAD
};

std::vector<vespalib::string> mode_names = {
    "grow",
    "shrink",
    "perm-crash",
    "temp-crash",
    "replace"
};

vespalib::string bad_mode_name("bad");

Mode get_mode(const vespalib::string& mode_name) {
    for (uint32_t i = 0; i < mode_names.size(); ++i) {
        if (mode_name == mode_names[i]) {
            return static_cast<Mode>(i);
        }
    }
    return Mode::BAD;
}

vespalib::string& get_mode_name(Mode mode) {
    uint32_t i = static_cast<uint32_t>(mode);
    return (i < mode_names.size()) ? mode_names[i] : bad_mode_name;
}

enum ReFeedMode {
    NONE,
    PUT,
    UPDATE,
    BAD
};

std::vector<vespalib::string> refeed_mode_names = {
    "none",
    "put",
    "update"
};

ReFeedMode get_refeed_mode(const vespalib::string& refeed_mode_name) {
    for (uint32_t i = 0; i < refeed_mode_names.size(); ++i) {
        if (refeed_mode_name == refeed_mode_names[i]) {
            return static_cast<ReFeedMode>(i);
        }
    }
    return ReFeedMode::BAD;
}

class BMParams : public BmClusterParams,
                 public BmFeedParams
{
    uint32_t _flip_nodes;
    Mode     _mode;
    ReFeedMode _refeed_mode;
    bool     _use_feed_settle;
public:
    BMParams();
    uint32_t get_flip_nodes() const noexcept { return _flip_nodes; }
    Mode get_mode() const noexcept { return _mode; }
    ReFeedMode get_refeed_mode() const noexcept { return _refeed_mode; }
    bool get_use_feed_settle() const noexcept { return _use_feed_settle; }
    void set_flip_nodes(uint32_t value) { _flip_nodes = value; }
    void set_mode(Mode value) { _mode = value; }
    void set_refeed_mode(ReFeedMode value) { _refeed_mode = value; }
    void set_use_feed_settle(bool value) { _use_feed_settle = value; }
    bool check() const;
};

BMParams::BMParams()
        : BmClusterParams(),
          BmFeedParams(),
          _flip_nodes(1u),
          _mode(Mode::GROW),
          _refeed_mode(ReFeedMode::NONE),
          _use_feed_settle(false)
{
    set_enable_service_layer(true);
    set_enable_distributor(true);
    set_use_document_api(true);
    set_nodes_per_group(4);
}


bool
BMParams::check() const
{
    if (!BmClusterParams::check()) {
        return false;
    }
    if (!BmFeedParams::check()) {
        return false;
    }
    if (get_num_nodes() < 2u) {
        std::cerr << "Too few nodes: " << get_num_nodes() << std::endl;
        return false;
    }
    if (_mode == Mode::REPLACE) {
        if (_flip_nodes * 2 > get_num_nodes()) {
            std::cerr << "Too many flip nodes (" << _flip_nodes << ") with " << get_num_nodes() << " nodes (replace mode)" << std::endl;
            return false;
        }
    } else {
        if (_flip_nodes >= get_num_nodes()) {
            std::cerr << "Too many flip nodes (" << _flip_nodes << ") with " << get_num_nodes() << " nodes (" << get_mode_name(_mode) << " mode)" << std::endl;
            return false;
        }
    }
    if (_mode == Mode::BAD) {
        std::cerr << "Bad mode" << std::endl;
        return false;
    }
    if (_refeed_mode == ReFeedMode::BAD) {
        std::cerr << "Bad refeed-mode" << std::endl;
        return false;
    }
    return true;
}

class ReFeed {
    vespalib::ThreadStackExecutor           _top_executor;
    vespalib::ThreadStackExecutor           _executor;
    BmFeeder                                _feeder;
    const vespalib::string                  _op_name;
    const BMParams&                         _params;
    int64_t&                                _time_bias;
    const std::vector<vespalib::nbostream>& _feed;

    void run();
public:
    ReFeed(const BMParams& params, std::shared_ptr<const DocumentTypeRepo> repo, IBmFeedHandler& feed_handler, int64_t& time_bias, const std::vector<vespalib::nbostream>& feed, const vespalib::string& op_name);
    ~ReFeed();
};

ReFeed::ReFeed(const BMParams& params, std::shared_ptr<const DocumentTypeRepo> repo, IBmFeedHandler& feed_handler, int64_t& time_bias, const std::vector<vespalib::nbostream>& feed, const vespalib::string& op_name)
    : _top_executor(1, 128_Ki),
      _executor(params.get_client_threads(), 128_Ki),
      _feeder(repo, feed_handler, _executor),
      _op_name(op_name),
      _params(params),
      _time_bias(time_bias),
      _feed(feed)
{
    _top_executor.execute(makeLambdaTask([this]()
                                         { run(); }));
}

ReFeed::~ReFeed()
{
    _feeder.stop();
    _top_executor.sync();
    _top_executor.shutdown();
}

void
ReFeed::run()
{
    std::this_thread::sleep_for(2s);
    _feeder.run_feed_tasks_loop(_time_bias, _feed, _params, _op_name);
}

}

class Benchmark {
    BMParams                                   _params;
    std::shared_ptr<const DocumenttypesConfig> _document_types;
    std::shared_ptr<const DocumentTypeRepo>    _repo;
    std::unique_ptr<BmCluster>                 _cluster;
    BmFeed                                     _feed;
    std::vector<vespalib::nbostream>           _put_feed;
    std::vector<vespalib::nbostream>           _update_feed;
    int64_t                                    _time_bias;

    void adjust_cluster_state_before_feed();
    void adjust_cluster_state_after_feed();
    void adjust_cluster_state_after_first_redistribution();
    void make_feed();
    void feed();
    std::chrono::duration<double> redistribute();

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
      _feed(_repo),
      _put_feed(),
      _update_feed(),
      _time_bias(std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch() - 24h).count())
{
    _cluster->make_nodes();
}

Benchmark::~Benchmark() = default;

void
Benchmark::adjust_cluster_state_before_feed()
{
    auto& dist = _cluster->get_real_distribution();
    auto& mode_name = get_mode_name(_params.get_mode());
    switch (_params.get_mode()) {
    case Mode::GROW:
    case Mode::REPLACE:
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i, State::DOWN);
        }
        LOG(info, "Mode %s: Taking down %u node(s) initially", mode_name.c_str(), _params.get_flip_nodes());
        break;
    default:
        LOG(info, "Mode %s: No cluster state adjust before feed", mode_name.c_str());
    }
    dist.commit_cluster_state_change();
}

void
Benchmark::adjust_cluster_state_after_feed()
{
    auto& dist = _cluster->get_real_distribution();
    auto& mode_name = get_mode_name(_params.get_mode());
    switch (_params.get_mode()) {
    case Mode::GROW:
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i, State::UP);
        }
        LOG(info, "Mode %s: taking up %u node(s)", mode_name.c_str(), _params.get_flip_nodes());
        break;
    case Mode::SHRINK:
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i, State::RETIRED);
        }
        LOG(info, "Mode %s: Retiring %u node(s)", mode_name.c_str(), _params.get_flip_nodes());
        break;
    case Mode::PERM_CRASH:
    case Mode::TEMP_CRASH:
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i, State::DOWN);
        }
        LOG(info, "Mode %s: taking down %u node(s)", mode_name.c_str(), _params.get_flip_nodes());
        break;
    case Mode::REPLACE:
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i, State::UP);
        }
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i + _params.get_flip_nodes(), State::RETIRED);
        }
        LOG(info, "Mode %s: Taking up %u node(s) and retiring %u node(s)", mode_name.c_str(), _params.get_flip_nodes(), _params.get_flip_nodes());
        break;
    default:
        LOG(info, "Mode %s: No cluster state adjust after feed", mode_name.c_str());
    }
    dist.commit_cluster_state_change();
}

void
Benchmark::adjust_cluster_state_after_first_redistribution()
{
    auto& dist = _cluster->get_real_distribution();
    auto& mode_name = get_mode_name(_params.get_mode());
    switch (_params.get_mode()) {
    case Mode::TEMP_CRASH:
        for (uint32_t i = 0; i < _params.get_flip_nodes(); ++i) {
            dist.set_node_state(i, State::UP);
        }
        LOG(info, "Mode %s: taking up %u node(s)", mode_name.c_str(), _params.get_flip_nodes());
        break;
    default:
        LOG(info, "Mode %s: No cluster state adjust after first redistribution", mode_name.c_str());
    }
    dist.commit_cluster_state_change();
}

void
Benchmark::make_feed()
{
    vespalib::ThreadStackExecutor executor(_params.get_client_threads(), 128_Ki);
    _put_feed = _feed.make_feed(executor, _params, [this](BmRange range, BucketSelector bucket_selector) { return _feed.make_put_feed(range, bucket_selector); }, _feed.num_buckets(), "put");
    if (_params.get_refeed_mode() == ReFeedMode::UPDATE) {
        _update_feed = _feed.make_feed(executor, _params, [this](BmRange range, BucketSelector bucket_selector) { return _feed.make_update_feed(range, bucket_selector); }, _feed.num_buckets(), "update");
    }
}

void
Benchmark::feed()
{
    vespalib::ThreadStackExecutor executor(_params.get_client_threads(), 128_Ki);
    BmFeeder feeder(_repo, *_cluster->get_feed_handler(), executor);
    BmNodeStatsReporter reporter(*_cluster, false);
    reporter.start(500ms);
    LOG(info, "Feed handler is '%s'", feeder.get_feed_handler().get_name().c_str());
    AvgSampler sampler;
    feeder.run_feed_tasks(0, _time_bias, _put_feed, _params, sampler, "put");
    reporter.report_now();
    if (_params.get_use_feed_settle()) {
        LOG(info, "Settling feed");
        std::this_thread::sleep_for(2s);
        reporter.report_now();
    }
}


std::chrono::duration<double>
Benchmark::redistribute()
{
    BmNodeStatsReporter reporter(*_cluster, true);
    auto before = std::chrono::steady_clock::now();
    reporter.start(500ms);
    _cluster->propagate_cluster_state();
    reporter.report_now();
    std::unique_ptr<ReFeed> refeed;
    switch (_params.get_refeed_mode()) {
    case ReFeedMode::PUT:
        refeed = std::make_unique<ReFeed>(_params, _repo, *_cluster->get_feed_handler(), _time_bias, _put_feed, "put");
        break;
    case ReFeedMode::UPDATE:
        refeed = std::make_unique<ReFeed>(_params, _repo, *_cluster->get_feed_handler(), _time_bias, _update_feed, "update");
        break;
    default:
        ;
    }
    for (;;) {
        auto duration = std::chrono::steady_clock::now() - reporter.get_change_time();
        if (duration >= 6s) {
            break;
        }
        std::this_thread::sleep_for(100ms);
    }
    refeed.reset();
    return reporter.get_change_time() - before;
}

void
Benchmark::run()
{
    adjust_cluster_state_before_feed();
    _cluster->start(_feed);
    make_feed();
    feed();
    LOG(info, "--------------------------------");
    auto old_snapshot = _cluster->get_bucket_db_snapshots();
    adjust_cluster_state_after_feed();
    auto elapsed = redistribute();
    auto new_snapshot = _cluster->get_bucket_db_snapshots();
    uint32_t moved_docs = new_snapshot.count_moved_documents(old_snapshot);
    uint32_t lost_unique_docs = new_snapshot.count_lost_unique_documents(old_snapshot);
    LOG(info, "Redistributed %u docs in %5.3f seconds, %4.2f docs/s, %u lost unique docs", moved_docs, elapsed.count(), moved_docs / elapsed.count(), lost_unique_docs);
    if (_params.get_mode() == Mode::TEMP_CRASH) {
        if (_params.get_use_feed_settle()) {
            LOG(info, "Settling redistribution");
            std::this_thread::sleep_for(2s);
        }
        adjust_cluster_state_after_first_redistribution();
        elapsed = redistribute();
        LOG(info, "Cleanup of %u docs in %5.3f seconds, %4.2f docs/s, %u refound unique docs", moved_docs, elapsed.count(), moved_docs / elapsed.count(), lost_unique_docs);
    }
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
        "vespa-redistribute-bm version 0.0\n"
        "\n"
        "USAGE:\n";
    std::cerr <<
        "vespa-redistribute-bm\n"
        "[--bucket-db-stripe-bits bits]\n"
        "[--client-threads threads]\n"
        "[--distributor-merge-busy-wait distributor-merge-busy-wait]\n"
        "[--distributor-stripes stripes]\n"
        "[--doc-store-chunk-compression-level level]\n"
        "[--doc-store-chunk-maxbytes maxbytes]\n"
        "[--documents documents]\n"
        "[--flip-nodes flip-nodes]\n"
        "[--groups groups]\n"
        "[--ignore-merge-queue-limit]\n"
        "[--indexing-sequencer [latency,throughput,adaptive]]\n"
        "[--max-merges-per-node max-merges-per-node]\n"
        "[--max-merge-queue-size max-merge-queue-size]\n"
        "[--max-pending max-pending]\n"
        "[--max-pending-idealstate-operations max-pending-idealstate-operations]\n"
        "[--mbus-distributor-node-max-pending-count count]\n"
        "[--mode [grow, shrink, perm-crash, temp-crash, replace]\n"
        "[--nodes-per-group nodes-per-group]\n"
        "[--redundancy redundancy]\n"
        "[--refeed-mode [none, put, update]\n"
        "[--rpc-events-before-wakeup events]\n"
        "[--rpc-network-threads threads]\n"
        "[--rpc-targets-per-node targets]\n"
        "[--response-threads threads]\n"
        "[--use-async-message-handling]\n"
        "[--use-feed-settle]" << std::endl;
}

bool
App::get_options(int argc, char **argv)
{
    int c;
    int long_opt_index = 0;
    static struct option long_opts[] = {
        { "bucket-db-stripe-bits", 1, nullptr, 0 },
        { "client-threads", 1, nullptr, 0 },
        { "distributor-merge-busy-wait", 1, nullptr, 0 },
        { "distributor-stripes", 1, nullptr, 0 },
        { "doc-store-chunk-compression-level", 1, nullptr, 0 },
        { "doc-store-chunk-maxbytes", 1, nullptr, 0 },
        { "documents", 1, nullptr, 0 },
        { "flip-nodes", 1, nullptr, 0 },
        { "groups", 1, nullptr, 0 },
        { "ignore-merge-queue-limit", 0, nullptr, 0 },
        { "indexing-sequencer", 1, nullptr, 0 },
        { "max-merges-per-node", 1, nullptr, 0 },
        { "max-merge-queue-size", 1, nullptr, 0 },
        { "max-pending", 1, nullptr, 0 },
        { "max-pending-idealstate-operations", 1, nullptr, 0 },
        { "mbus-distributor-node-max-pending-count", 1, nullptr, 0 },
        { "mode", 1, nullptr, 0 },
        { "nodes-per-group", 1, nullptr, 0 },
        { "redundancy", 1, nullptr, 0 },
        { "refeed-mode", 1, nullptr, 0 },
        { "response-threads", 1, nullptr, 0 },
        { "rpc-events-before-wakeup", 1, nullptr, 0 },
        { "rpc-network-threads", 1, nullptr, 0 },
        { "rpc-targets-per-node", 1, nullptr, 0 },
        { "use-async-message-handling", 0, nullptr, 0 },
        { "use-feed-settle", 0, nullptr, 0 },
        { nullptr, 0, nullptr, 0 }
    };
    enum longopts_enum {
        LONGOPT_BUCKET_DB_STRIPE_BITS,
        LONGOPT_CLIENT_THREADS,
        LONGOPT_DISTRIBUTOR_MERGE_BUSY_WAIT,
        LONGOPT_DISTRIBUTOR_STRIPES,
        LONGOPT_DOC_STORE_CHUNK_COMPRESSION_LEVEL,
        LONGOPT_DOC_STORE_CHUNK_MAXBYTES,
        LONGOPT_DOCUMENTS,
        LONGOPT_FLIP_NODES,
        LONGOPT_GROUPS,
        LONGOPT_IGNORE_MERGE_QUEUE_LIMIT,
        LONGOPT_INDEXING_SEQUENCER,
        LONGOPT_MAX_MERGES_PER_NODE,
        LONGOPT_MAX_MERGE_QUEUE_SIZE,
        LONGOPT_MAX_PENDING,
        LONGOPT_MAX_PENDING_IDEALSTATE_OPERATIONS,
        LONGOPT_MBUS_DISTRIBUTOR_NODE_MAX_PENDING_COUNT,
        LONGOPT_MODE,
        LONGOPT_NODES_PER_GROUP,
        LONGOPT_REDUNDANCY,
        LONGOPT_REFEED,
        LONGOPT_RESPONSE_THREADS,
        LONGOPT_RPC_EVENTS_BEFORE_WAKEUP,
        LONGOPT_RPC_NETWORK_THREADS,
        LONGOPT_RPC_TARGETS_PER_NODE,
        LONGOPT_USE_ASYNC_MESSAGE_HANDLING,
        LONGOPT_USE_FEED_SETTLE
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
            case LONGOPT_DISTRIBUTOR_MERGE_BUSY_WAIT:
                _bm_params.set_distributor_merge_busy_wait(atoi(optarg));
                break;
            case LONGOPT_DISTRIBUTOR_STRIPES:
                _bm_params.set_distributor_stripes(atoi(optarg));
                break;
            case LONGOPT_DOC_STORE_CHUNK_COMPRESSION_LEVEL:
                _bm_params.set_doc_store_chunk_compression_level(atoi(optarg));
                break;
            case LONGOPT_DOC_STORE_CHUNK_MAXBYTES:
                _bm_params.set_doc_store_chunk_maxbytes(atoi(optarg));
                break;
            case LONGOPT_DOCUMENTS:
                _bm_params.set_documents(atoi(optarg));
                break;
            case LONGOPT_FLIP_NODES:
                _bm_params.set_flip_nodes(atoi(optarg));
                break;
            case LONGOPT_GROUPS:
                _bm_params.set_groups(atoi(optarg));
                break;
            case LONGOPT_IGNORE_MERGE_QUEUE_LIMIT:
                _bm_params.set_disable_queue_limits_for_chained_merges(true);
                break;
            case LONGOPT_INDEXING_SEQUENCER:
                _bm_params.set_indexing_sequencer(optarg);
                break;
            case LONGOPT_MAX_MERGES_PER_NODE:
                _bm_params.set_max_merges_per_node(atoi(optarg));
                break;
            case LONGOPT_MAX_MERGE_QUEUE_SIZE:
                _bm_params.set_max_merge_queue_size(atoi(optarg));
                break;
            case LONGOPT_MAX_PENDING:
                _bm_params.set_max_pending(atoi(optarg));
                break;
            case LONGOPT_MAX_PENDING_IDEALSTATE_OPERATIONS:
                _bm_params.set_max_pending_idealstate_operations(atoi(optarg));
                break;
            case LONGOPT_MBUS_DISTRIBUTOR_NODE_MAX_PENDING_COUNT:
                _bm_params.set_mbus_distributor_node_max_pending_count(atoi(optarg));
                break;
            case LONGOPT_MODE:
                _bm_params.set_mode(get_mode(optarg));
                if (_bm_params.get_mode() == Mode::BAD) {
                    std::cerr << "Unknown mode name " << optarg << std::endl;
                }
                break;
            case LONGOPT_NODES_PER_GROUP:
                _bm_params.set_nodes_per_group(atoi(optarg));
                break;
            case LONGOPT_REDUNDANCY:
                _bm_params.set_redundancy(atoi(optarg));
                break;
            case LONGOPT_REFEED:
                _bm_params.set_refeed_mode(get_refeed_mode(optarg));
                if (_bm_params.get_refeed_mode() == ReFeedMode::BAD) {
                    std::cerr << "Unknown refeed-mode name " << optarg << std::endl;
                }
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
            case LONGOPT_USE_ASYNC_MESSAGE_HANDLING:
                _bm_params.set_use_async_message_handling_on_schedule(true);
                break;
            case LONGOPT_USE_FEED_SETTLE:
                _bm_params.set_use_feed_settle(true);
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
    DummyFileHeaderContext::setCreator("vespa-redistribute-bm");
    App app;
    auto exit_value = app.main(argc, argv);
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    return exit_value;
}
