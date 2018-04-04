// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <map>
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/bootstrapconfigmanager.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/i_document_db_config_owner.h>
#include <vespa/searchcore/proton/server/proton_config_snapshot.h>
#include <vespa/searchcore/proton/server/proton_configurer.h>
#include <vespa/searchcore/proton/server/i_proton_configurer_owner.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/config-bucketspaces.h>

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
using document::DocumenttypesConfig;
using document::DocumenttypesConfigBuilder;
using search::TuneFileDocumentDB;
using std::map;
using search::index::Schema;
using search::index::SchemaBuilder;
using proton::matching::RankingConstants;

struct DBConfigFixture {
    using UP = std::unique_ptr<DBConfigFixture>;
    AttributesConfigBuilder _attributesBuilder;
    RankProfilesConfigBuilder _rankProfilesBuilder;
    RankingConstantsConfigBuilder _rankingConstantsBuilder;
    IndexschemaConfigBuilder _indexschemaBuilder;
    SummaryConfigBuilder _summaryBuilder;
    SummarymapConfigBuilder _summarymapBuilder;
    JuniperrcConfigBuilder _juniperrcBuilder;
    ImportedFieldsConfigBuilder _importedFieldsBuilder;

    Schema::SP buildSchema()
    {
        Schema::SP schema(std::make_shared<Schema>());
        SchemaBuilder::build(_attributesBuilder, *schema);
        SchemaBuilder::build(_summaryBuilder, *schema);
        SchemaBuilder::build(_indexschemaBuilder, *schema);
        return schema;
    }

    RankingConstants::SP buildRankingConstants()
    {
        return std::make_shared<RankingConstants>();
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
             std::make_shared<IndexschemaConfig>(_indexschemaBuilder),
             std::make_shared<AttributesConfig>(_attributesBuilder),
             std::make_shared<SummaryConfig>(_summaryBuilder),
             std::make_shared<SummarymapConfig>(_summarymapBuilder),
             std::make_shared<JuniperrcConfig>(_juniperrcBuilder),
             documentTypes,
             repo,
             std::make_shared<ImportedFieldsConfig>(_importedFieldsBuilder),
             std::make_shared<TuneFileDocumentDB>(),
             buildSchema(),
             std::make_shared<DocumentDBMaintenanceConfig>(),
             search::LogDocumentStore::Config(),
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

    ConfigFixture(const std::string & id)
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
        addDocType("_alwaysthere_");
    }

    ~ConfigFixture() { }

    DBConfigFixture *addDocType(const std::string & name) {
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
    }

    BootstrapConfig::SP getBootstrapConfig(int64_t generation) const {
        return BootstrapConfig::SP(new BootstrapConfig(generation,
                                                       BootstrapConfig::DocumenttypesConfigSP(new DocumenttypesConfig(_documenttypesBuilder)),
                                                       std::shared_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(_documenttypesBuilder)),
                                                       BootstrapConfig::ProtonConfigSP(new ProtonConfig(_protonBuilder)),
                                                       std::make_shared<FiledistributorrpcConfig>(),
                                                       std::make_shared<BucketspacesConfig>(_bucketspacesBuilder),
                                                       std::make_shared<TuneFileDocumentDB>(), HwInfo()));
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

struct MyProtonConfigurerOwner;

struct MyDocumentDBConfigOwner : public IDocumentDBConfigOwner
{
    vespalib::string _name;
    MyProtonConfigurerOwner &_owner;
    MyDocumentDBConfigOwner(const vespalib::string &name,
                            MyProtonConfigurerOwner &owner)
        : IDocumentDBConfigOwner(),
          _name(name),
          _owner(owner)
    {
    }
    ~MyDocumentDBConfigOwner() { }

    void reconfigure(const DocumentDBConfig::SP & config) override;
};

struct MyProtonConfigurerOwner : public IProtonConfigurerOwner
{
    using InitializeThreads = std::shared_ptr<vespalib::ThreadStackExecutorBase>;
    vespalib::ThreadStackExecutor _executor;
    std::map<DocTypeName, std::shared_ptr<MyDocumentDBConfigOwner>> _dbs;
    std::vector<vespalib::string> _log;

    MyProtonConfigurerOwner()
        : IProtonConfigurerOwner(),
          _executor(1, 128 * 1024),
          _dbs(),
          _log()
    {
    }
    virtual ~MyProtonConfigurerOwner() { }

    virtual IDocumentDBConfigOwner *addDocumentDB(const DocTypeName &docTypeName,
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
        ASSERT_TRUE(_dbs.find(docTypeName) == _dbs.end());
        auto db = std::make_shared<MyDocumentDBConfigOwner>(docTypeName.getName(), *this);
        _dbs.insert(std::make_pair(docTypeName, db));
        std::ostringstream os;
        os << "add db " << docTypeName.getName() << " " << documentDBConfig->getGeneration();
        _log.push_back(os.str());
        return db.get();
    }
    virtual void removeDocumentDB(const DocTypeName &docTypeName) override {
        ASSERT_FALSE(_dbs.find(docTypeName) == _dbs.end());
        _dbs.erase(docTypeName);
        std::ostringstream os;
        os << "remove db " << docTypeName.getName();
        _log.push_back(os.str());
    }
    virtual void applyConfig(const std::shared_ptr<BootstrapConfig> &bootstrapConfig) override {
        std::ostringstream os;
        os << "apply config " << bootstrapConfig->getGeneration();
        _log.push_back(os.str());
    }
    void reconfigureDocumentDB(const vespalib::string &name, const DocumentDBConfig::SP &config)
    {
        std::ostringstream os;
        os << "reconf db " << name << " " << config->getGeneration();
        _log.push_back(os.str());
    }
    void sync() { _executor.sync(); }
};

void
MyDocumentDBConfigOwner::reconfigure(const DocumentDBConfig::SP & config)
{
    _owner.reconfigureDocumentDB(_name, config);
}

struct Fixture
{
    MyProtonConfigurerOwner _owner;
    ConfigFixture _config;
    ProtonConfigurer _configurer;

    Fixture()
        : _owner(),
          _config("test"),
          _configurer(_owner._executor, _owner)
    {
    }
    ~Fixture() { }

    void assertLog(const std::vector<vespalib::string> &expLog) {
        EXPECT_EQUAL(expLog, _owner._log);
    }
    void sync() { _owner.sync(); }
    void addDocType(const vespalib::string &name) { _config.addDocType(name); }
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

TEST_F("require that nothing is applied before initial config", Fixture())
{
    f.applyConfig();
    TEST_DO(f1.assertLog({}));
}

TEST_F("require that initial config is applied", Fixture())
{
    f.applyInitialConfig();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2"}));
}

TEST_F("require that new config is blocked", Fixture())
{
    f.applyInitialConfig();
    f.reconfigure();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2"}));
}

TEST_F("require that new config can be unblocked", Fixture())
{
    f.applyInitialConfig();
    f.reconfigure();
    f.allowReconfig();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2", "apply config 3", "reconf db _alwaysthere_ 3"}));
}

TEST_F("require that initial config is not reapplied due to config unblock", Fixture())
{
    f.applyInitialConfig();
    f.allowReconfig();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2"}));
}

TEST_F("require that we can add document db", Fixture())
{
    f.applyInitialConfig();
    f.allowReconfig();
    f.addDocType("foobar");
    f.reconfigure();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2", "apply config 3","reconf db _alwaysthere_ 3", "add db foobar 3"}));
}

TEST_F("require that we can remove document db", Fixture())
{
    f.addDocType("foobar");
    f.applyInitialConfig();
    f.allowReconfig();
    f.removeDocType("foobar");
    f.reconfigure();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2", "add db foobar 2", "apply config 3","reconf db _alwaysthere_ 3", "remove db foobar"}));
}

TEST_F("require that document db adds and reconfigs are intermingled", Fixture())
{
    f.addDocType("foobar");
    f.applyInitialConfig();
    f.allowReconfig();
    f.addDocType("abar");
    f.removeDocType("foobar");
    f.addDocType("foobar");
    f.addDocType("zbar");
    f.reconfigure();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2", "add db foobar 2", "apply config 3","reconf db _alwaysthere_ 3", "add db abar 3", "reconf db foobar 3", "add db zbar 3"}));
}

TEST_F("require that document db removes are applied at end", Fixture())
{
    f.addDocType("abar");
    f.addDocType("foobar");
    f.applyInitialConfig();
    f.allowReconfig();
    f.removeDocType("abar");
    f.reconfigure();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2", "add db abar 2", "add db foobar 2", "apply config 3","reconf db _alwaysthere_ 3", "reconf db foobar 3", "remove db abar"}));
}

TEST_F("require that new configs can be blocked again", Fixture())
{
    f.applyInitialConfig();
    f.reconfigure();
    f.allowReconfig();
    f.disableReconfig();
    f.reconfigure();
    TEST_DO(f1.assertLog({"apply config 2", "add db _alwaysthere_ 2", "apply config 3", "reconf db _alwaysthere_ 3"}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
