// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/functiontablefactory.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchcommon/attribute/config.h>

#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include <unistd.h>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;

using search::AttributeVector;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::StringAttribute;

typedef search::attribute::Config           AVC;
typedef search::attribute::BasicType        AVBT;
typedef search::attribute::CollectionType AVCT;

typedef AttributeVector::SP             AttributePtr;

using CollectionType = FieldInfo::CollectionType;

class Benchmark : public FtTestApp {
public:
    typedef std::vector<std::pair<vespalib::string, vespalib::string> > KeyValueVector;

    class Config {
    private:
        typedef std::map<vespalib::string, vespalib::string> StringMap;
        StringMap _config;

        bool isKnown(const vespalib::string & key) const;

    public:
        Config() : _config() {}
        Config(const vespalib::string & fileName) : _config() {
            init(fileName);
        }
        void init(const vespalib::string & fileName);

        void add(const vespalib::string & key, const vespalib::string & value) {
            _config[key] = value;
        }

        void addIfNotFound(const vespalib::string & key, const vespalib::string & value) {
            if (_config.count(key) == 0) {
                add(key, value);
            }
        }

        // known config values
        vespalib::string getCase(const vespalib::string & fallback = "") const {
            return getAsStr("case", fallback);
        }
        vespalib::string getFeature(const vespalib::string & fallback = "") const {
            return getAsStr("feature", fallback);
        }
        vespalib::string getIndex(const vespalib::string & fallback = "") const {
            return getAsStr("index", fallback);
        }
        vespalib::string getQuery(const vespalib::string & fallback = "") const {
            return getAsStr("query", fallback);
        }
        vespalib::string getField(const vespalib::string & fallback = "") const {
            return getAsStr("field", fallback);
        }
        uint32_t getNumRuns(uint32_t fallback = 1000) const {
            return getAsUint32("numruns", fallback);
        }

        // access "unknown" config values
        vespalib::string getAsStr(const vespalib::string & key, const vespalib::string & fallback = "") const {
            StringMap::const_iterator itr = _config.find(key);
            if (itr != _config.end()) {
                return vespalib::string(itr->second);
            }
            return vespalib::string(fallback);
        }
        uint32_t getAsUint32(const vespalib::string & key, uint32_t fallback = 0) const {
            return util::strToNum<uint32_t>(getAsStr(key, vespalib::make_string("%u", fallback)));
        }
        double getAsDouble(const vespalib::string & key, double fallback = 0) const {
            return util::strToNum<double>(getAsStr(key, vespalib::make_string("%f", fallback)));
        }

        KeyValueVector getUnknown() const;

        friend std::ostream & operator << (std::ostream & os, const Config & cfg);
    };

private:
    search::fef::BlueprintFactory _factory;
    vespalib::Timer _timer;
    vespalib::duration _sample;

    void start() { _timer = vespalib::Timer(); }
    void sample() { _sample = _timer.elapsed(); }
    void setupPropertyMap(Properties & props, const KeyValueVector & values);
    void runFieldMatch(Config & cfg);
    void runRankingExpression(Config & cfg);

    AttributePtr createAttributeVector(AVBT dt, const vespalib::string & name, const vespalib::string & ctype, uint32_t numDocs,
                                       AttributeVector::largeint_t value, uint32_t valueCount);
    AttributePtr createAttributeVector(const vespalib::string & name, const vespalib::string & ctype, uint32_t numDocs,
                                       AttributeVector::largeint_t value, uint32_t valueCount);
    AttributePtr createStringAttributeVector(const vespalib::string & name, const vespalib::string & ctype, uint32_t numDocs,
                                             const std::vector<vespalib::string> & values);
    void runAttributeMatch(Config & cfg);
    void runAttribute(Config & cfg);
    void runDotProduct(Config & cfg);
    void runNativeAttributeMatch(Config & cfg);
    void runNativeFieldMatch(Config & cfg);
    void runNativeProximity(Config & cfg);

public:
    Benchmark();
    ~Benchmark();
    int Main() override;

};

TEST_APPHOOK(Benchmark);

Benchmark::Benchmark() : _factory(), _timer(), _sample() {}
Benchmark::~Benchmark() {}

bool
Benchmark::Config::isKnown(const vespalib::string & key) const
{
    if (key == vespalib::string("case") ||
        key == vespalib::string("feature") ||
        key == vespalib::string("index") ||
        key == vespalib::string("query") ||
        key == vespalib::string("field") ||
        key == vespalib::string("numruns"))
    {
        return true;
    }
    return false;
}

void
Benchmark::Config::init(const vespalib::string & fileName)
{
    std::ifstream is(fileName.c_str());
    if (is.fail()) {
        throw std::runtime_error(fileName);
    }

    while (is.good()) {
        std::string line;
        std::getline(is, line);
        if (!line.empty()) {
            std::vector<vespalib::string> values = FtUtil::tokenize(line, "=");
            assert(values.size() == 2);
            add(values[0], values[1]);
        }
    }
}

Benchmark::KeyValueVector
Benchmark::Config::getUnknown() const
{
    KeyValueVector retval;
    for (StringMap::const_iterator itr = _config.begin(); itr != _config.end(); ++itr) {
        if (!isKnown(itr->first)) {
            retval.push_back(std::make_pair(itr->first, itr->second));
        }
    }
    return retval;
}

std::ostream & operator << (std::ostream & os, const Benchmark::Config & cfg)
{
    std::cout << "getCase:    '" << cfg.getCase() << "'" << std::endl;
    std::cout << "getFeature: '" << cfg.getFeature() << "'" << std::endl;
    std::cout << "getIndex:   '" << cfg.getIndex() << "'" << std::endl;
    std::cout << "getQuery:   '" << cfg.getQuery() << "'" << std::endl;
    std::cout << "getField:   '" << cfg.getField() << "'" << std::endl;
    std::cout << "getNumRuns: '" << cfg.getNumRuns() << "'" << std::endl;

    for (StringMap::const_iterator itr = cfg._config.begin(); itr != cfg._config.end(); ++itr) {
        os << "'" << itr->first << "'='" << itr->second << "'" << std::endl;
    }
    return os;
}


void
Benchmark::setupPropertyMap(Properties & props, const KeyValueVector & values)
{
    std::cout << "**** setup property map ****" << std::endl;
    for (uint32_t i = 0; i < values.size(); ++i) {
        std::cout << "'" << values[i].first << "'='" << values[i].second << "'" << std::endl;
        props.add(values[i].first, values[i].second);
    }
    std::cout << "**** setup property map ****" << std::endl;
}

void
Benchmark::runFieldMatch(Config & cfg)
{
    cfg.addIfNotFound("feature", "fieldMatch(foo)");
    cfg.addIfNotFound("index",   "foo");
    cfg.addIfNotFound("query",   "a b c d");
    cfg.addIfNotFound("field",   "a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    vespalib::string index = cfg.getIndex();
    vespalib::string query = cfg.getQuery();
    vespalib::string field = cfg.getField();
    uint32_t numRuns = cfg.getNumRuns();

    FtFeatureTest ft(_factory, feature);

    setupPropertyMap(ft.getIndexEnv().getProperties(), cfg.getUnknown());
    setupFieldMatch(ft, index, query, field, NULL, 0, 0.0f, 0);

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

void
Benchmark::runRankingExpression(Config & cfg)
{
    cfg.addIfNotFound("feature", "rankingExpression");
    cfg.addIfNotFound("rankingExpression.rankingScript", "1 + 1 + 1 + 1");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    uint32_t numRuns = cfg.getNumRuns();

    FtFeatureTest ft(_factory, feature);
    setupPropertyMap(ft.getIndexEnv().getProperties(), cfg.getUnknown());
    ASSERT_TRUE(ft.setup());

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

AttributePtr
Benchmark::createAttributeVector(const vespalib::string & name, const vespalib::string & ctype, uint32_t numDocs,
                                 AttributeVector::largeint_t value, uint32_t valueCount)
{
    return createAttributeVector(AVBT::INT32, name, ctype, numDocs, value, valueCount);
}

AttributePtr
Benchmark::createAttributeVector(AVBT dt, const vespalib::string & name, const vespalib::string & ctype, uint32_t numDocs,
                                 AttributeVector::largeint_t value, uint32_t valueCount)
{
    AttributePtr a;
    if (ctype == "single") {
        a = AttributeFactory::createAttribute(name, AVC(dt,  AVCT::SINGLE));
        std::cout << "create single int32" << std::endl;
    } else if (ctype == "array") {
        a = AttributeFactory::createAttribute(name, AVC(dt,  AVCT::ARRAY));
        std::cout << "create array int32" << std::endl;
    } else if (ctype == "wset") {
        a = AttributeFactory::createAttribute(name, AVC(dt,  AVCT::WSET));
        std::cout << "create wset int32" << std::endl;
    }

    a->addDocs(numDocs);
    IntegerAttribute * ia = static_cast<IntegerAttribute *>(a.get());
    for (uint32_t i = 0; i < numDocs; ++i) {
        if (ctype == "single") {
            ia->update(i, value);
        } else {
            for (uint32_t j = 0; j < valueCount; ++j) {
                if (ctype == "array") {
                    ia->append(i, value, 0);
                } else {
                    ia->append(i, value + j, j);
                }
            }
        }
    }

    a->commit();
    return a;
}

AttributePtr
Benchmark::createStringAttributeVector(const vespalib::string & name, const vespalib::string & ctype, uint32_t numDocs,
                                       const std::vector<vespalib::string> & values)
{
    AttributePtr a;
    if (ctype == "single") {
        a = AttributeFactory::createAttribute(name, AVC(AVBT::STRING,  AVCT::SINGLE));
        std::cout << "create single string" << std::endl;
    } else if (ctype == "array") {
        a = AttributeFactory::createAttribute(name, AVC(AVBT::STRING,  AVCT::ARRAY));
        std::cout << "create array string" << std::endl;
    } else if (ctype == "wset") {
        a = AttributeFactory::createAttribute(name, AVC(AVBT::STRING,  AVCT::WSET));
        std::cout << "create wset string" << std::endl;
    }

    a->addDocs(numDocs);
    StringAttribute * sa = static_cast<StringAttribute *>(a.get());
    for (uint32_t i = 0; i < numDocs; ++i) {
        if (ctype == "single") {
            sa->update(i, values[0]);
        } else {
            for (uint32_t j = 0; j < values.size(); ++j) {
                sa->append(i, values[j], j);
            }
        }
    }

    a->commit();
    return a;
}

void
Benchmark::runAttributeMatch(Config & cfg)
{
    cfg.addIfNotFound("feature", "attributeMatch(foo)");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    uint32_t numRuns = 1000000;
    uint32_t numDocs = 1000000;

    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
    ft.getIndexEnv().getAttributeMap().add(createAttributeVector("foo", "single", numDocs, 10, 10));
    ft.getQueryEnv().getBuilder().addAttributeNode("foo");
    setupPropertyMap(ft.getIndexEnv().getProperties(), cfg.getUnknown());
    ASSERT_TRUE(ft.setup());
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
    mdb->setWeight("foo", 0, 0);
    mdb->apply(0);
    TermFieldMatchData *amd = mdb->getTermFieldMatchData(0, 0);

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        {
            amd->reset(0); // preserve old behavior
            TermFieldMatchDataPosition pos;
            pos.setElementWeight(i % numDocs);
            amd->appendPosition(pos);
        }
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

void
Benchmark::runAttribute(Config & cfg)
{
    cfg.addIfNotFound("feature", "attribute(foo,str4)");
    cfg.addIfNotFound("numruns", "10000000");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    uint32_t numRuns = cfg.getNumRuns();
    uint32_t numDocs = cfg.getAsUint32("numdocs", 1000);
    StringList values;
    values.add("str0").add("str1").add("str2").add("str3").add("str4")
          .add("str5").add("str6").add("str7").add("str8").add("str9");

    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "foo");
    ft.getIndexEnv().getAttributeMap().add(createStringAttributeVector("foo", "wset", numDocs, values));
    ASSERT_TRUE(ft.setup());
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

void
Benchmark::runDotProduct(Config & cfg)
{
    cfg.addIfNotFound("feature", "dotProduct(wsstr,vector)");
    cfg.addIfNotFound("numruns", "1000000");
    cfg.addIfNotFound("numdocs", "1000");
    cfg.addIfNotFound("numvalues", "10");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    vespalib::string collectionType = cfg.getAsStr("collectiontype", "wset");
    vespalib::string dataType = cfg.getAsStr("datatype", "string");
    uint32_t numRuns = cfg.getNumRuns();
    uint32_t numDocs = cfg.getAsUint32("numdocs", 1000);
    uint32_t numValues = cfg.getAsUint32("numvalues", 10);
    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE,
                                           collectionType == "wset" ? CollectionType::WEIGHTEDSET : CollectionType::ARRAY,
                                           "wsstr");
    if (dataType == "string") {
        StringList values;
        for (uint32_t i = 0; i < numValues; ++i) {
            values.add(vespalib::make_string("str%u", i));
        }

        ft.getIndexEnv().getAttributeMap().add(createStringAttributeVector("wsstr", collectionType, numDocs, values));
    } else if (dataType == "int") {
        ft.getIndexEnv().getAttributeMap().add(createAttributeVector(AVBT::INT32, "wsstr", collectionType, numDocs, 0, numValues));
    } else if (dataType == "long") {
        ft.getIndexEnv().getAttributeMap().add(createAttributeVector(AVBT::INT64, "wsstr", collectionType, numDocs, 0, numValues));
    } else if (dataType == "float") {
        ft.getIndexEnv().getAttributeMap().add(createAttributeVector(AVBT::FLOAT, "wsstr", collectionType, numDocs, 0, numValues));
    } else if (dataType == "double") {
        ft.getIndexEnv().getAttributeMap().add(createAttributeVector(AVBT::DOUBLE, "wsstr", collectionType, numDocs, 0, numValues));
    } else {
        std::cerr << "Illegal data type '" << dataType << std::endl;
    }
    ft.getQueryEnv().getProperties().add("dotProduct.vector", cfg.getAsStr("dotProduct.vector", "(str0:1)"));
    ASSERT_TRUE(ft.setup());
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

void
Benchmark::runNativeAttributeMatch(Config & cfg)
{
    cfg.addIfNotFound("feature", "nativeAttributeMatch(foo)");
    cfg.addIfNotFound("numruns", "10000000");
    cfg.addIfNotFound("numdocs", "1000000");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    uint32_t numRuns = cfg.getNumRuns();
    uint32_t numDocs = cfg.getAsUint32("numdocs");

    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
    ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(256))); // same as backend
    ft.getQueryEnv().getBuilder().addAttributeNode("foo")->setWeight(search::query::Weight(100));
    setupPropertyMap(ft.getIndexEnv().getProperties(), cfg.getUnknown());
    ASSERT_TRUE(ft.setup());
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
    mdb->setWeight("foo", 0, 0);
    mdb->apply(0);

    TermFieldMatchData *amd = mdb->getTermFieldMatchData(0, 0);

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        uint32_t docId = i % numDocs;
        {
            amd->reset(docId);
            TermFieldMatchDataPosition pos;
            pos.setElementWeight(docId);
            amd->appendPosition(pos);
        }
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

void
Benchmark::runNativeFieldMatch(Config & cfg)
{
    cfg.addIfNotFound("feature", "nativeFieldMatch(foo)");
    cfg.addIfNotFound("numruns", "10000000");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    uint32_t numRuns = cfg.getNumRuns();

    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(256))); // same as backend
    std::vector<vespalib::string> searchedFields;
    searchedFields.push_back("foo");
    ft.getQueryEnv().getBuilder().addIndexNode(searchedFields);
    setupPropertyMap(ft.getIndexEnv().getProperties(), cfg.getUnknown());
    ASSERT_TRUE(ft.setup());
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

    // setup occurrence data
    mdb->setFieldLength("foo", 100);
    mdb->addOccurence("foo", 0, 2);
    mdb->addOccurence("foo", 0, 8);
    mdb->addOccurence("foo", 0, 32);
    mdb->addOccurence("foo", 0, 64);
    ASSERT_TRUE(mdb->apply(0));

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

void
Benchmark::runNativeProximity(Config & cfg)
{
    cfg.addIfNotFound("feature", "nativeProximity(foo)");
    cfg.addIfNotFound("numruns", "10000000");

    std::cout << "**** config ****" << std::endl;
    std::cout << cfg << std::endl;
    std::cout << "**** config ****" << std::endl;

    vespalib::string feature = cfg.getFeature();
    uint32_t numRuns = cfg.getNumRuns();

    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(256))); // same as backend
    std::vector<vespalib::string> searchedFields;
    searchedFields.push_back("foo");
    ft.getQueryEnv().getBuilder().addIndexNode(searchedFields); // termId 0
    ft.getQueryEnv().getBuilder().addIndexNode(searchedFields); // termId 1
    setupPropertyMap(ft.getIndexEnv().getProperties(), cfg.getUnknown());
    ASSERT_TRUE(ft.setup());
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

    // setup occurrence data
    mdb->setFieldLength("foo", 100);
    mdb->addOccurence("foo", 0, 2);
    mdb->addOccurence("foo", 0, 16);
    mdb->addOccurence("foo", 0, 32);
    mdb->addOccurence("foo", 1, 6);
    mdb->addOccurence("foo", 1, 12);
    mdb->addOccurence("foo", 1, 30);
    ASSERT_TRUE(mdb->apply(0));

    start();
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;
    for (uint32_t i = 0; i < numRuns; ++i) {
        // rank evaluation is now lazy, please re-write benchmark if needed
    }
    sample();
}

int
Benchmark::Main()
{
    TEST_INIT("featurebenchmark");

    // Configure factory with all known blueprints.
    setup_fef_test_plugin(_factory);
    setup_search_features(_factory);

    int opt;
    bool optError = false;
    vespalib::string file;
    vespalib::string feature;
    while ((opt = getopt(_argc, _argv, "c:f:")) != -1) {
        switch (opt) {
        case 'c':
            file.assign(optarg);
            break;
        case 'f':
            feature.assign(optarg);
            break;
        default:
            optError = true;
            break;
        }
    }

    if (_argc != optind || optError) {
        //usage();
        return -1;
    }

    Config cfg;
    if (file.empty()) {
        cfg.add("case", feature);
    } else {
        cfg.init(file);
    }

    if (cfg.getCase() == vespalib::string("fieldMatch")) {
        runFieldMatch(cfg);
    } else if (cfg.getCase() == vespalib::string("rankingExpression")) {
        runRankingExpression(cfg);
    } else if (cfg.getCase() == vespalib::string("attributeMatch")) {
        runAttributeMatch(cfg);
    } else if (cfg.getCase() == vespalib::string("attribute")) {
        runAttribute(cfg);
    } else if (cfg.getCase() == vespalib::string("dotProduct")) {
        runDotProduct(cfg);
    } else if (cfg.getCase() == vespalib::string("nativeAttributeMatch")) {
        runNativeAttributeMatch(cfg);
    } else if (cfg.getCase() == vespalib::string("nativeFieldMatch")) {
        runNativeFieldMatch(cfg);
    } else if (cfg.getCase() == vespalib::string("nativeProximity")) {
        runNativeProximity(cfg);
    } else {
        std::cout << "feature case '" << cfg.getCase() << "' is not known" << std::endl;
    }

    std::cout << "TET:  " << vespalib::count_ms(_sample) << " (ms)" << std::endl;
    std::cout << "ETPD: " << std::fixed << std::setprecision(10) << double(vespalib::count_ms(_sample)) / cfg.getNumRuns() << " (ms)" << std::endl;
    std::cout << "**** '" << cfg.getFeature() << "' ****" << std::endl;

    TEST_DONE();
    return 0;
}

