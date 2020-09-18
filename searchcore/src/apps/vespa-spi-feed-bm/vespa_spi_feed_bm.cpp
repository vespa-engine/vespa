// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <tests/proton/common/dummydbowner.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summarymap.h>
#include <vespa/fastos/file.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
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
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/fastos/app.h>
#include <getopt.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("vespa-spi-feed-bm");

using namespace config;
using namespace proton;
using namespace cloud::config::filedistribution;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using namespace std::chrono_literals;
using vespa::config::content::core::BucketspacesConfig;

using document::AssignValueUpdate;
using document::BucketId;
using document::BucketSpace;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumenttypesConfig;
using document::DocumentUpdate;
using document::Field;
using document::FieldUpdate;
using document::IntFieldValue;
using document::test::makeBucketSpace;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::index::SchemaBuilder;
using search::transactionlog::TransLogServer;
using storage::spi::Bucket;
using storage::spi::PartitionId;
using storage::spi::PersistenceProvider;
using storage::spi::Priority;
using storage::spi::Timestamp;
using storage::spi::Trace;
using vespalib::makeLambdaTask;

using DocumentDBMap = std::map<DocTypeName, std::shared_ptr<DocumentDB>>;

namespace {

storage::spi::LoadType default_load_type(0, "default");

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
            "client",
            doc_type_name.getName());
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

class MyPendingTracker {
    uint32_t                _pending;
    uint32_t                _limit;
    std::mutex              _mutex;
    std::condition_variable _cond;

public:
    MyPendingTracker(uint32_t limit)
        : _pending(0u),
          _limit(limit),
          _mutex(),
          _cond()
    {
    }

    ~MyPendingTracker()
    {
        drain();
    }

    void release() {
        std::unique_lock<std::mutex> guard(_mutex);
        --_pending;
        if (_pending < _limit) {
            _cond.notify_all();
        }
        //LOG(info, "release, pending is now %u", _pending);
    }
    void retain() {
        std::unique_lock<std::mutex> guard(_mutex);
        while (_pending >= _limit) {
            _cond.wait(guard);
        }
        ++_pending;
        //LOG(info, "retain, pending is now %u", _pending);
    }

    void drain() {
        std::unique_lock<std::mutex> guard(_mutex);
        while (_pending > 0) {
            _cond.wait(guard);
        }
    }
};

class MyOperationComplete : public storage::spi::OperationComplete
{
    MyPendingTracker& _tracker;
public:
    MyOperationComplete(MyPendingTracker &tracker);
    ~MyOperationComplete();
    void onComplete(std::unique_ptr<storage::spi::Result> result) override;
    void addResultHandler(const storage::spi::ResultHandler* resultHandler) override;
};

MyOperationComplete::MyOperationComplete(MyPendingTracker& tracker)
    : _tracker(tracker)
{
    _tracker.retain();
}

MyOperationComplete::~MyOperationComplete()
{
    _tracker.release();
}

void
MyOperationComplete::onComplete(std::unique_ptr<storage::spi::Result> result)
{
    (void) result;
}

void
MyOperationComplete::addResultHandler(const storage::spi::ResultHandler * resultHandler)
{
    (void) resultHandler;
}

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
    uint32_t _threads;
    uint32_t _update_passes;
    uint32_t get_start(uint32_t thread_id) const {
        return (_documents / _threads) * thread_id + std::min(thread_id, _documents % _threads);
    }
public:
    BMParams()
        : _documents(160000),
          _threads(32),
          _update_passes(1)
    {
    }
    BMRange get_range(uint32_t thread_id) const {
        return BMRange(get_start(thread_id), get_start(thread_id + 1));
    }
    uint32_t get_documents() const { return _documents; }
    uint32_t get_threads() const { return _threads; }
    uint32_t get_update_passes() const { return _update_passes; }
    void set_documents(uint32_t documents_in) { _documents = documents_in; }
    void set_threads(uint32_t threads_in) { _threads = threads_in; }
    void set_update_passes(uint32_t update_passes_in) { _update_passes = update_passes_in; }
    bool check() const;
};

bool
BMParams::check() const
{
    if (_threads < 1) {
        std::cerr << "Too few threads: " << _threads << std::endl;
        return false;
    }
    if (_threads > 256) {
        std::cerr << "Too many threads: " << _threads << std::endl;
        return false;
    }
    if (_documents < _threads) {
        std::cerr << "Too few documents: " << _documents << std::endl;
        return false;
    }
    return true;
}

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
    std::shared_ptr<PersistenceEngine>         _persistence_engine;
    storage::spi::Context                      _context;
    uint32_t                                   _bucket_bits;

    PersistenceProviderFixture();
    ~PersistenceProviderFixture();
    void create_document_db();
    uint32_t num_buckets() const { return (1u << _bucket_bits); }
    Bucket make_bucket(uint32_t i) const { return Bucket(document::Bucket(_bucket_space, BucketId(_bucket_bits, i & (num_buckets() - 1))), PartitionId(0)); }
    DocumentId make_document_id(uint32_t i) const;
    std::unique_ptr<Document> make_document(uint32_t i) const;
    std::unique_ptr<DocumentUpdate> make_document_update(uint32_t i) const;
    void create_buckets();
};

PersistenceProviderFixture::PersistenceProviderFixture()
    : _document_types(make_document_type()),
      _repo(std::make_shared<DocumentTypeRepo>(*_document_types)),
      _doc_type_name("test"),
      _document_type(_repo->getDocumentType(_doc_type_name.getName())),
      _field(_document_type->getField("int")),
      _document_db_config(make_document_db_config(_document_types, _repo, _doc_type_name)),
      _base_dir(base_dir),
      _file_header_context(),
      _tls_listen_port(9017),
      _tls("tls", _tls_listen_port, _base_dir, _file_header_context),
      _tls_spec(vespalib::make_string("tcp/localhost:%d", _tls_listen_port)),
      _query_limiter(),
      _clock(),
      _metrics_wire_service(),
      _config_stores(),
      _summary_executor(8, 128 * 1024),
      _document_db_owner(),
      _bucket_space(makeBucketSpace(_doc_type_name.getName())),
      _document_db(),
      _persistence_owner(),
      _write_filter(),
      _persistence_engine(),
      _context(default_load_type, Priority(0), Trace::TraceLevel(0)),
      _bucket_bits(16)
{
    create_document_db();
    _persistence_engine = std::make_unique<PersistenceEngine>(_persistence_owner, _write_filter, -1, false);
    auto proxy = std::make_shared<PersistenceHandlerProxy>(_document_db);
    _persistence_engine->putHandler(_persistence_engine->getWLock(), _bucket_space, _doc_type_name, proxy);
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
PersistenceProviderFixture::create_document_db()
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
    auto bootstrap_config = std::make_shared<BootstrapConfig>(1,
                                                              _document_types,
                                                              _repo,
                                                              std::make_shared<ProtonConfig>(),
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
                                                _tls,
                                                _metrics_wire_service,
                                                _file_header_context,
                                                _config_stores.getConfigStore(_doc_type_name.toString()),
                                                std::make_shared<vespalib::ThreadStackExecutor>(16, 128 * 1024),
                                                HwInfo());
    _document_db->start();
    _document_db->waitForOnlineState();
}

DocumentId
PersistenceProviderFixture::make_document_id(uint32_t i) const
{
    DocumentId id(vespalib::make_string("id::test:n=%u:%u", i & (num_buckets() - 1), i));
    return id;
}

std::unique_ptr<Document>
PersistenceProviderFixture::make_document(uint32_t i) const
{
    auto id = make_document_id(i);
    auto document = std::make_unique<Document>(*_document_type, id);
    document->setRepo(*_repo);
    document->setFieldValue(_field, std::make_unique<IntFieldValue>(i));
    return document;
}

std::unique_ptr<DocumentUpdate>
PersistenceProviderFixture::make_document_update(uint32_t i) const
{
    auto id = make_document_id(i);
    auto document_update = std::make_unique<DocumentUpdate>(*_repo, *_document_type, id);
    document_update->addUpdate(FieldUpdate(_field).addUpdate(AssignValueUpdate(IntFieldValue(15))));
    return document_update;
}

void
PersistenceProviderFixture::create_buckets()
{
    auto &provider = *_persistence_engine;
    for (unsigned int i = 0; i < num_buckets(); ++i) {
        provider.createBucket(make_bucket(i), _context);
    }
}

void
put_async_task(PersistenceProviderFixture &f, BMRange range, int64_t time_bias)
{
    LOG(debug, "put_async_task([%u..%u))", range.get_start(), range.get_end());
    MyPendingTracker pending_tracker(100);
    auto &provider = *f._persistence_engine;
    auto &context = f._context;
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto bucket = f.make_bucket(i);
        auto document = f.make_document(i);
        provider.putAsync(bucket, Timestamp(time_bias + i), std::move(document), context, std::make_unique<MyOperationComplete>(pending_tracker));
    }
    pending_tracker.drain();
}

void
run_put_async_tasks(PersistenceProviderFixture &f, vespalib::ThreadStackExecutor &executor, int pass, int64_t& time_bias, const BMParams& bm_params)
{
    LOG(info, "putAsync %u small documents, pass=%u", bm_params.get_documents(), pass);
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, range, time_bias]()
                                        { put_async_task(f, range, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    LOG(info, "%8.2f puts/s for pass=%u", bm_params.get_documents() / elapsed.count(), pass);
    time_bias += bm_params.get_documents();
}

void
update_async_task(PersistenceProviderFixture &f, BMRange range, int64_t time_bias)
{
    LOG(debug, "update_async_task([%u..%u))", range.get_start(), range.get_end());
    MyPendingTracker pending_tracker(100);
    auto &provider = *f._persistence_engine;
    auto &context = f._context;
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto bucket = f.make_bucket(i);
        auto document_update = f.make_document_update(i);
        provider.updateAsync(bucket, Timestamp(time_bias + i), std::move(document_update), context, std::make_unique<MyOperationComplete>(pending_tracker));
    }
    pending_tracker.drain();
}

void
run_update_async_tasks(PersistenceProviderFixture &f, vespalib::ThreadStackExecutor &executor, int pass, int64_t& time_bias, const BMParams& bm_params)
{
    LOG(info, "updateAsync %u small documents, pass=%u", bm_params.get_documents(), pass);
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, range, time_bias]()
                                        { update_async_task(f, range, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    LOG(info, "%8.2f updates/s for pass=%u", bm_params.get_documents() / elapsed.count(), pass);
    time_bias += bm_params.get_documents();
}

void
remove_async_task(PersistenceProviderFixture &f, BMRange range, int64_t time_bias)
{
    LOG(debug, "remove_async_task([%u..%u))", range.get_start(), range.get_end());
    MyPendingTracker pending_tracker(100);
    auto &provider = *f._persistence_engine;
    auto &context = f._context;
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto bucket = f.make_bucket(i);
        auto document_id = f.make_document_id(i);
        provider.removeAsync(bucket, Timestamp(time_bias + i), document_id, context, std::make_unique<MyOperationComplete>(pending_tracker));
    }
    pending_tracker.drain();
}

void
run_remove_async_tasks(PersistenceProviderFixture &f, vespalib::ThreadStackExecutor &executor, int pass, int64_t& time_bias, const BMParams &bm_params)
{
    LOG(info, "removeAsync %u small documents, pass=%u", bm_params.get_documents(), pass);
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < bm_params.get_threads(); ++i) {
        auto range = bm_params.get_range(i);
        executor.execute(makeLambdaTask([&f, range, time_bias]()
                                        { remove_async_task(f, range, time_bias); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    LOG(info, "%8.2f removes/s for pass=%u", bm_params.get_documents() / elapsed.count(), pass);
    time_bias += bm_params.get_documents();
}

void benchmark_async_spi(const BMParams &bm_params)
{
    vespalib::rmdir(base_dir, true);
    PersistenceProviderFixture f;
    auto &provider = *f._persistence_engine;
    LOG(info, "start initialize");
    provider.initialize();
    LOG(info, "create %u buckets", f.num_buckets());
    f.create_buckets();
    vespalib::ThreadStackExecutor executor(bm_params.get_threads(), 128 * 1024);
    int64_t time_bias = 1;
    run_put_async_tasks(f, executor, 0, time_bias, bm_params);
    run_put_async_tasks(f, executor, 1, time_bias, bm_params);
    for (uint32_t pass = 0; pass < bm_params.get_update_passes(); ++pass) {
        run_update_async_tasks(f, executor, pass, time_bias, bm_params);
    }
    run_remove_async_tasks(f, executor, 0, time_bias, bm_params);
    run_remove_async_tasks(f, executor, 1, time_bias, bm_params);
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
        "vespa-spi-feed-bm version 0.0\n"
        "\n"
        "USAGE:\n";
    std::cerr <<
        "vespa-spi-feed-bm\n"
        "[--threads threads]\n"
        "[--documents documents]"
        "[--update-passes update-passes]" << std::endl;
}

bool
App::get_options()
{
    int c;
    const char *opt_argument = nullptr;
    int long_opt_index = 0;
    static struct option long_opts[] = {
        { "threads", 1, nullptr, 0 },
        { "documents", 1, nullptr, 0 },
        { "update-passes", 1, nullptr, 0 }
    };
    enum longopts_enum {
        LONGOPT_THREADS,
        LONGOPT_DOCUMENTS,
        LONGOPT_UPDATE_PASSES
    };
    int opt_index = 1;
    resetOptIndex(opt_index);
    while ((c = GetOptLong("", opt_argument, opt_index, long_opts, &long_opt_index)) != -1) {
        switch (c) {
        case 0:
            switch(long_opt_index) {
            case LONGOPT_THREADS:
                _bm_params.set_threads(atoi(opt_argument));
                break;
            case LONGOPT_DOCUMENTS:
                _bm_params.set_documents(atoi(opt_argument));
                break;
            case LONGOPT_UPDATE_PASSES:
                _bm_params.set_update_passes(atoi(opt_argument));
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
    DummyFileHeaderContext::setCreator("vespa-spi-feed-bm");
    App app;
    auto exit_value = app.Entry(argc, argv);
    vespalib::rmdir(base_dir, true);
    return exit_value;
}
