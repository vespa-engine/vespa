// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <map>
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/document_db_config_owner.h>
#include <vespa/searchcore/proton/server/proton_config_snapshot.h>
#include <vespa/searchcore/proton/server/proton_configurer.h>
#include <vespa/searchcore/proton/server/i_proton_configurer_owner.h>
#include <vespa/searchcore/proton/server/i_proton_disk_layout.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;
using namespace proton;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using namespace cloud::config::filedistribution;
using vespa::config::content::core::BucketspacesConfig;
using vespa::config::content::core::BucketspacesConfigBuilder;

using InitializeThreads = std::shared_ptr<vespalib::ThreadStackExecutorBase>;
using config::ConfigUri;
using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using std::map;
using search::index::Schema;
using search::fef::OnnxModels;
using search::fef::RankingConstants;
using search::fef::RankingExpressions;

struct DBConfigFixture {
    using UP = std::unique_ptr<DBConfigFixture>;
    AttributesConfigBuilder _attributesBuilder;
    RankProfilesConfigBuilder _rankProfilesBuilder;
    IndexschemaConfigBuilder _indexschemaBuilder;
    SummaryConfigBuilder _summaryBuilder;
    JuniperrcConfigBuilder _juniperrcBuilder;
    ImportedFieldsConfigBuilder _importedFieldsBuilder;

    Schema::SP buildSchema()
    {
        return DocumentDBConfig::build_schema(_attributesBuilder, _indexschemaBuilder);
    }

    static std::shared_ptr<const RankingConstants> buildRankingConstants()
    {
        return std::make_shared<RankingConstants>();
    }

    static std::shared_ptr<const RankingExpressions> buildRankingExpressions()
    {
        return std::make_shared<RankingExpressions>();
    }

    static std::shared_ptr<const OnnxModels> buildOnnxModels()
    {
        return std::make_shared<OnnxModels>();
    }

    DocumentDBConfig::SP getConfig(int64_t generation,
                                   std::shared_ptr<DocumenttypesConfig> documentTypes,
                                   std::shared_ptr<const DocumentTypeRepo> repo,
                                   const vespalib::string &configId,
                                   const vespalib::string &docTypeName)
    {
        return std::make_shared<DocumentDBConfig>
            (generation,
             std::make_shared<RankProfilesConfig>(_rankProfilesBuilder),
             buildRankingConstants(),
             buildRankingExpressions(),
             buildOnnxModels(),
             std::make_shared<IndexschemaConfig>(_indexschemaBuilder),
             std::make_shared<AttributesConfig>(_attributesBuilder),
             std::make_shared<SummaryConfig>(_summaryBuilder),
             std::make_shared<JuniperrcConfig>(_juniperrcBuilder),
             std::move(documentTypes),
             std::move(repo),
             std::make_shared<ImportedFieldsConfig>(_importedFieldsBuilder),
             std::make_shared<TuneFileDocumentDB>(),
             buildSchema(),
             std::make_shared<DocumentDBMaintenanceConfig>(),
             search::LogDocumentStore::Config(),
             ThreadingServiceConfig::make(),
             AllocConfig::makeDefault(),
             configId,
             docTypeName);
    }
};

struct ConfigFixture {
    const std::string _configId;
    ProtonConfigBuilder _protonBuilder;
    DocumenttypesConfigBuilder _documenttypesBuilder;
    FiledistributorrpcConfigBuilder _filedistBuilder;
    BucketspacesConfigBuilder _bucketspacesBuilder;
    map<std::string, DBConfigFixture::UP> _dbConfig;
    int _idcounter;
    int64_t _generation;
    std::shared_ptr<ProtonConfigSnapshot> _cachedConfigSnapshot;

    explicit ConfigFixture(const std::string & id)
        : _configId(id),
          _protonBuilder(),
          _documenttypesBuilder(),
          _filedistBuilder(),
          _bucketspacesBuilder(),
          _dbConfig(),
          _idcounter(-1),
          _generation(1),
          _cachedConfigSnapshot()
    {
        addDocType("_alwaysthere_", "default");
    }

    ~ConfigFixture();

    DBConfigFixture *addDocType(const std::string & name, const std::string& bucket_space) {
        DocumenttypesConfigBuilder::Documenttype dt;
        dt.bodystruct = -1270491200;
        dt.headerstruct = 306916075;
        dt.id = _idcounter--;
        dt.name = name;
        dt.version = 0;
        _documenttypesBuilder.documenttype.push_back(dt);

        ProtonConfigBuilder::Documentdb db;
        db.inputdoctypename = name;
        db.configid = _configId + "/" + name;
        _protonBuilder.documentdb.push_back(db);

        BucketspacesConfigBuilder::Documenttype bsdt;
        bsdt.name = name;
        bsdt.bucketspace = bucket_space;
        _bucketspacesBuilder.documenttype.push_back(bsdt);

        DBConfigFixture::UP fixture = std::make_unique<DBConfigFixture>();
        return _dbConfig.emplace(std::make_pair(name, std::move(fixture))).first->second.get();
    }

    void removeDocType(const std::string & name)
    {
        for (auto it(_documenttypesBuilder.documenttype.begin()),
                 mt(_documenttypesBuilder.documenttype.end());
             it != mt;
             it++) {
            if ((*it).name.compare(name) == 0) {
                _documenttypesBuilder.documenttype.erase(it);
                break;
            }
        }

        for (auto it(_protonBuilder.documentdb.begin()),
                 mt(_protonBuilder.documentdb.end());
             it != mt;
             it++) {
            if ((*it).inputdoctypename.compare(name) == 0) {
                _protonBuilder.documentdb.erase(it);
                break;
            }
        }
        _dbConfig.erase(name);
        for (auto it(_bucketspacesBuilder.documenttype.begin()), mt(_bucketspacesBuilder.documenttype.end()); it != mt; ++it) {
            if (it->name == name) {
                _bucketspacesBuilder.documenttype.erase(it);
                break;
            }
        }
    }

    BootstrapConfig::SP getBootstrapConfig(int64_t generation) const {
        return std::make_shared<BootstrapConfig>(generation,
                                                 std::make_shared<DocumenttypesConfig>(_documenttypesBuilder),
                                                 std::make_shared<DocumentTypeRepo>(_documenttypesBuilder),
                                                 std::make_shared<ProtonConfig>(_protonBuilder),
                                                 std::make_shared<FiledistributorrpcConfig>(),
                                                 std::make_shared<BucketspacesConfig>(_bucketspacesBuilder),
                                                 std::make_shared<TuneFileDocumentDB>(), HwInfo());
    }

    std::shared_ptr<ProtonConfigSnapshot> getConfigSnapshot()
    {
        if (_cachedConfigSnapshot) {
            return _cachedConfigSnapshot;
        }
        int64_t generation = ++_generation;
        auto bootstrap = getBootstrapConfig(generation);
        std::map<DocTypeName, DocumentDBConfig::SP> dbconfigs;
        auto doctypes = bootstrap->getDocumenttypesConfigSP();
        auto repo = bootstrap->getDocumentTypeRepoSP();
        for (auto &db : _dbConfig) {
            DocTypeName name(db.first);
            dbconfigs.insert(std::make_pair(name,
                                            db.second->getConfig(generation,
                                                                 doctypes,
                                                                 repo,
                                                                 _configId + "/" + db.first,
                                                                 db.first)));
        }
        _cachedConfigSnapshot = std::make_shared<ProtonConfigSnapshot>(bootstrap, dbconfigs);
        return _cachedConfigSnapshot;
    }
    void newConfig() { _cachedConfigSnapshot.reset(); }

};

ConfigFixture::~ConfigFixture() = default;

struct MyProtonConfigurerOwner;

struct MyDocumentDBConfigOwner : public DocumentDBConfigOwner
{
    vespalib::string _name;
    document::BucketSpace _bucket_space;
    MyProtonConfigurerOwner &_owner;
    MyDocumentDBConfigOwner(const vespalib::string &name,
                            document::BucketSpace bucket_space,
                            MyProtonConfigurerOwner &owner)
        : DocumentDBConfigOwner(),
          _name(name),
          _bucket_space(bucket_space),
          _owner(owner)
    {
    }
    ~MyDocumentDBConfigOwner() override;

    void reconfigure(DocumentDBConfig::SP config) override;
    document::BucketSpace getBucketSpace() const override { return _bucket_space; }
};

struct MyLog
{
    std::vector<vespalib::string> _log;

    MyLog()
        : _log()
    {
    }
    ~MyLog();

    void appendLog(const vespalib::string & logEntry)
    {
        _log.emplace_back(logEntry);
    }
};

MyLog::~MyLog() = default;

struct MyProtonConfigurerOwner : public IProtonConfigurerOwner,
                                 public MyLog
{
    vespalib::ThreadStackExecutor _executor;
    std::map<DocTypeName, std::shared_ptr<MyDocumentDBConfigOwner>> _dbs;

    MyProtonConfigurerOwner()
        : IProtonConfigurerOwner(),
          MyLog(),
          _executor(1),
          _dbs()
    {
    }
    ~MyProtonConfigurerOwner() override;

    std::shared_ptr<DocumentDBConfigOwner> addDocumentDB(const DocTypeName &docTypeName,
                                                                 document::BucketSpace bucketSpace,
                                                                 const vespalib::string &configId,
                                                                 const std::shared_ptr<BootstrapConfig> &bootstrapConfig,
                                                                 const std::shared_ptr<DocumentDBConfig> &documentDBConfig,
                                                                 InitializeThreads initializeThreads) override
    {
        (void) bucketSpace;
        (void) configId;
        (void) bootstrapConfig;
        (void) initializeThreads;
        EXPECT_TRUE(_dbs.find(docTypeName) == _dbs.end());
        auto db = std::make_shared<MyDocumentDBConfigOwner>(docTypeName.getName(), bucketSpace, *this);
        _dbs.insert(std::make_pair(docTypeName, db));
        std::ostringstream os;
        os << "add db " << docTypeName.getName() << " " << documentDBConfig->getGeneration();
        _log.emplace_back(os.str());
        return db;
    }
    void removeDocumentDB(const DocTypeName &docTypeName) override {
        ASSERT_FALSE(_dbs.find(docTypeName) == _dbs.end());
        _dbs.erase(docTypeName);
        std::ostringstream os;
        os << "remove db " << docTypeName.getName();
        _log.emplace_back(os.str());
    }
    void applyConfig(const std::shared_ptr<BootstrapConfig> &bootstrapConfig) override {
        std::ostringstream os;
        os << "apply config " << bootstrapConfig->getGeneration();
        _log.emplace_back(os.str());
        
    }
    void reconfigureDocumentDB(const vespalib::string &name, const DocumentDBConfig & config)
    {
        std::ostringstream os;
        os << "reconf db " << name << " " << config.getGeneration();
        _log.emplace_back(os.str());
    }
    void sync() { _executor.sync(); }
};

MyProtonConfigurerOwner::~MyProtonConfigurerOwner() = default;
MyDocumentDBConfigOwner::~MyDocumentDBConfigOwner() = default;

void
MyDocumentDBConfigOwner::reconfigure(DocumentDBConfig::SP config)
{
    _owner.reconfigureDocumentDB(_name, *config);
}

struct MyProtonDiskLayout : public IProtonDiskLayout
{
    MyLog &_log;

    explicit MyProtonDiskLayout(MyLog &myLog)
        : _log(myLog)
    {
    }
    void remove(const DocTypeName &docTypeName) override {
        std::ostringstream os;
        os << "remove dbdir " << docTypeName.getName();
        _log.appendLog(os.str());
    }
    void initAndPruneUnused(const std::set<DocTypeName> &docTypeNames) override {
        std::ostringstream os;
        os << "initial dbs ";
        bool first = true;
        for (const auto &docTypeName : docTypeNames) {
            if (!first) {
                os << ",";
            }
            first = false;
            os << docTypeName.getName();
        }
        _log.appendLog(os.str());
    }
};

class ProtonConfigurerTest : public ::testing::Test
{
    MyProtonConfigurerOwner _owner;
    ConfigFixture _config;
    std::unique_ptr<IProtonDiskLayout> _diskLayout;
    ProtonConfigurer _configurer;

protected:
    ProtonConfigurerTest()
        : _owner(),
          _config("test"),
          _diskLayout(),
          _configurer(_owner._executor, _owner, _diskLayout)
    {
        _diskLayout = std::make_unique<MyProtonDiskLayout>(_owner);
    }
    ~ProtonConfigurerTest() override;

    void assertLog(const std::vector<vespalib::string> &expLog) {
        EXPECT_EQ(expLog, _owner._log);
    }
    void sync() { _owner.sync(); }
    void addDocType(const vespalib::string &name, const std::string& bucket_space = "default") { _config.addDocType(name, bucket_space); }
    void removeDocType(const vespalib::string &name) { _config.removeDocType(name); }
    void applyConfig() {
        _configurer.reconfigure(_config.getConfigSnapshot());
        sync();
    }

    void applyInitialConfig() {
        applyConfig(); // sets initial pending config
        _configurer.applyInitialConfig(InitializeThreads());
    }
    void reconfigure() {
        _config.newConfig();
        applyConfig();
    }

    void allowReconfig() {
        _configurer.setAllowReconfig(true);
        sync();
    }
    void disableReconfig() {
        _configurer.setAllowReconfig(false);
    }
};

ProtonConfigurerTest::~ProtonConfigurerTest() = default;

TEST_F(ProtonConfigurerTest, require_that_nothing_is_applied_before_initial_config)
{
    applyConfig();
    assertLog({});
}

TEST_F(ProtonConfigurerTest, require_that_initial_config_is_applied)
{
    applyInitialConfig();
    assertLog({"initial dbs _alwaysthere_", "apply config 2", "add db _alwaysthere_ 2"});
}

TEST_F(ProtonConfigurerTest, require_that_new_config_is_blocked)
{
    applyInitialConfig();
    reconfigure();
    assertLog({"initial dbs _alwaysthere_", "apply config 2", "add db _alwaysthere_ 2"});
}

TEST_F(ProtonConfigurerTest, require_that_new_config_can_be_unblocked)
{
    applyInitialConfig();
    reconfigure();
    allowReconfig();
    assertLog({"initial dbs _alwaysthere_", "apply config 2", "add db _alwaysthere_ 2", "apply config 3", "reconf db _alwaysthere_ 3"});
}

TEST_F(ProtonConfigurerTest, require_that_initial_config_is_not_reapplied_due_to_config_unblock)
{
    applyInitialConfig();
    allowReconfig();
    assertLog({"initial dbs _alwaysthere_", "apply config 2", "add db _alwaysthere_ 2"});
}

TEST_F(ProtonConfigurerTest, require_that_we_can_add_document_db)
{
    applyInitialConfig();
    allowReconfig();
    addDocType("foobar");
    reconfigure();
    assertLog({"initial dbs _alwaysthere_", "apply config 2", "add db _alwaysthere_ 2", "apply config 3","reconf db _alwaysthere_ 3", "add db foobar 3"});
}

TEST_F(ProtonConfigurerTest, require_that_we_can_remove_document_db)
{
    addDocType("foobar");
    applyInitialConfig();
    allowReconfig();
    removeDocType("foobar");
    reconfigure();
    assertLog({"initial dbs _alwaysthere_,foobar", "apply config 2", "add db _alwaysthere_ 2", "add db foobar 2", "apply config 3","reconf db _alwaysthere_ 3", "remove db foobar", "remove dbdir foobar"});
}

TEST_F(ProtonConfigurerTest, require_that_document_db_adds_and_reconfigs_are_intermingled)
{
    addDocType("foobar");
    applyInitialConfig();
    allowReconfig();
    addDocType("abar");
    removeDocType("foobar");
    addDocType("foobar");
    addDocType("zbar");
    reconfigure();
    assertLog({"initial dbs _alwaysthere_,foobar", "apply config 2", "add db _alwaysthere_ 2", "add db foobar 2", "apply config 3","reconf db _alwaysthere_ 3", "add db abar 3", "reconf db foobar 3", "add db zbar 3"});
}

TEST_F(ProtonConfigurerTest, require_that_document_db_removes_are_applied_at_end)
{
    addDocType("abar");
    addDocType("foobar");
    applyInitialConfig();
    allowReconfig();
    removeDocType("abar");
    reconfigure();
    assertLog({"initial dbs _alwaysthere_,abar,foobar", "apply config 2", "add db _alwaysthere_ 2", "add db abar 2", "add db foobar 2", "apply config 3","reconf db _alwaysthere_ 3", "reconf db foobar 3", "remove db abar", "remove dbdir abar"});
}

TEST_F(ProtonConfigurerTest, require_that_new_configs_can_be_blocked_again)
{
    applyInitialConfig();
    reconfigure();
    allowReconfig();
    disableReconfig();
    reconfigure();
    assertLog({"initial dbs _alwaysthere_", "apply config 2", "add db _alwaysthere_ 2", "apply config 3", "reconf db _alwaysthere_ 3"});
}

TEST_F(ProtonConfigurerTest, require_that_bucket_space_for_document_type_change_exits)
{
    ::testing::FLAGS_gtest_death_test_style = "threadsafe";
    addDocType("globaldoc", "default");
    applyInitialConfig();
    removeDocType("globaldoc");
    addDocType("globaldoc", "global");
    allowReconfig();
    EXPECT_EXIT(reconfigure(), ::testing::ExitedWithCode(1), "Bucket space for document type globaldoc changed from default to global");
}


GTEST_MAIN_RUN_ALL_TESTS()
