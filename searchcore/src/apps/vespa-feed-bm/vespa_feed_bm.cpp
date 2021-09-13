// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fastos/app.h>
#include <vespa/searchcore/bmcluster/bm_cluster.h>
#include <vespa/searchcore/bmcluster/bm_cluster_controller.h>
#include <vespa/searchcore/bmcluster/bm_cluster_params.h>
#include <vespa/searchcore/bmcluster/bm_feed.h>
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
using search::bmcluster::BmClusterController;
using search::bmcluster::IBmFeedHandler;
using search::bmcluster::BmClusterParams;
using search::bmcluster::BmCluster;
using search::bmcluster::BmFeed;
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

class BMParams : public BmClusterParams {
    uint32_t _documents;
    uint32_t _client_threads;
    uint32_t _get_passes;
    uint32_t _put_passes;
    uint32_t _update_passes;
    uint32_t _remove_passes;
    uint32_t _max_pending;
    uint32_t get_start(uint32_t thread_id) const {
        return (_documents / _client_threads) * thread_id + std::min(thread_id, _documents % _client_threads);
    }
public:
    BMParams()
        : _documents(160000),
          _client_threads(1),
          _get_passes(0),
          _put_passes(2),
          _update_passes(1),
          _remove_passes(2),
          _max_pending(1000)
    {
    }
    BmRange get_range(uint32_t thread_id) const {
        return BmRange(get_start(thread_id), get_start(thread_id + 1));
    }
    uint32_t get_documents() const { return _documents; }
    uint32_t get_max_pending() const { return _max_pending; }
    uint32_t get_client_threads() const { return _client_threads; }
    uint32_t get_get_passes() const { return _get_passes; }
    uint32_t get_put_passes() const { return _put_passes; }
    uint32_t get_update_passes() const { return _update_passes; }
    uint32_t get_remove_passes() const { return _remove_passes; }
    void set_documents(uint32_t documents_in) { _documents = documents_in; }
    void set_max_pending(uint32_t max_pending_in) { _max_pending = max_pending_in; }
    void set_client_threads(uint32_t threads_in) { _client_threads = threads_in; }
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

    return true;
}

}

struct PersistenceProviderFixture {
    std::shared_ptr<const DocumenttypesConfig> _document_types;
    std::shared_ptr<const DocumentTypeRepo>    _repo;
    std::unique_ptr<BmCluster>                 _bm_cluster;
    BmFeed                                     _feed;
    IBmFeedHandler*                            _feed_handler;

    explicit PersistenceProviderFixture(const BMParams& params);
    ~PersistenceProviderFixture();
};

PersistenceProviderFixture::PersistenceProviderFixture(const BMParams& params)
    : _document_types(make_document_types()),
      _repo(document::DocumentTypeRepoFactory::make(*_document_types)),
      _bm_cluster(std::make_unique<BmCluster>(base_dir, base_port, params, _document_types, _repo)),
      _feed(_repo),
      _feed_handler(nullptr)
{
    _bm_cluster->make_nodes();
}

PersistenceProviderFixture::~PersistenceProviderFixture() = default;

std::vector<vespalib::nbostream>
make_feed(vespalib::ThreadStackExecutor &executor, const BMParams &bm_params, std::function<vespalib::nbostream(BmRange,BucketSelector)> func, uint32_t num_buckets, const vespalib::string &label)
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
    auto& feed = f._feed;
    auto& feed_handler = *f._feed_handler;
    uint32_t old_errors = feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&feed, &feed_handler, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { feed.put_async_task(feed_handler, max_pending, range, serialized_feed, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = feed_handler.get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "putAsync: pass=%u, errors=%u, puts/s: %8.2f", pass, new_errors, throughput);
    time_bias += bm_params.get_documents();
}

void
run_update_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass, int64_t& time_bias,
                       const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    auto& feed = f._feed;
    auto& feed_handler = *f._feed_handler;
    uint32_t old_errors = feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&feed, &feed_handler, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { feed.update_async_task(feed_handler, max_pending, range, serialized_feed, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = feed_handler.get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "updateAsync: pass=%u, errors=%u, updates/s: %8.2f", pass, new_errors, throughput);
    time_bias += bm_params.get_documents();
}

void
run_get_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass,
                       const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    auto& feed = f._feed;
    auto& feed_handler = *f._feed_handler;
    uint32_t old_errors = feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&feed, &feed_handler, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range]()
                                        { feed.get_async_task(feed_handler, max_pending, range, serialized_feed); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = feed_handler.get_error_count() - old_errors;
    double throughput = bm_params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "getAsync: pass=%u, errors=%u, gets/s: %8.2f", pass, new_errors, throughput);
}

void
run_remove_async_tasks(PersistenceProviderFixture& f, vespalib::ThreadStackExecutor& executor, int pass, int64_t& time_bias,
                       const std::vector<vespalib::nbostream>& serialized_feed_v, const BMParams& bm_params, AvgSampler& sampler)
{
    auto& feed = f._feed;
    auto& feed_handler = *f._feed_handler;
    uint32_t old_errors = feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_client_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&feed, &feed_handler, max_pending = bm_params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { feed.remove_async_task(feed_handler, max_pending, range, serialized_feed, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = feed_handler.get_error_count() - old_errors;
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
    auto& cluster = *f._bm_cluster;
    cluster.start(f._feed);
    f._feed_handler = cluster.get_feed_handler();
    vespalib::ThreadStackExecutor executor(bm_params.get_client_threads(), 128_Ki);
    auto& feed = f._feed;
    auto put_feed = make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_put_feed(range, bucket_selector); }, f._feed.num_buckets(), "put");
    auto update_feed = make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_update_feed(range, bucket_selector); }, f._feed.num_buckets(), "update");
    auto remove_feed = make_feed(executor, bm_params, [&feed](BmRange range, BucketSelector bucket_selector) { return feed.make_remove_feed(range, bucket_selector); }, f._feed.num_buckets(), "remove");
    int64_t time_bias = 1;
    LOG(info, "Feed handler is '%s'", f._feed_handler->get_name().c_str());
    benchmark_async_put(f, executor, time_bias, put_feed, bm_params);
    benchmark_async_update(f, executor, time_bias, update_feed, bm_params);
    benchmark_async_get(f, executor, remove_feed, bm_params);
    benchmark_async_remove(f, executor, time_bias, remove_feed, bm_params);
    LOG(info, "--------------------------------");

    f._feed_handler = nullptr;
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
