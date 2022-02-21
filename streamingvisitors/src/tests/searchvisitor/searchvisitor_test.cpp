// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/searchenvironment.h>
#include <vespa/searchvisitor/searchvisitor.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/persistence/spi/docentry.h>


#include <vespa/log/log.h>
LOG_SETUP("searchvisitor_test");

using namespace document;
using namespace search::query;
using namespace search;
using namespace storage;

namespace streaming {

class SearchVisitorTest : public vespalib::TestApp
{
private:
    framework::defaultimplementation::FakeClock _clock;
    StorageComponentRegisterImpl      _componentRegister;
    std::unique_ptr<StorageComponent> _component;
    SearchEnvironment                 _env;
    void testSearchVisitor();
    void testSearchEnvironment();
    void testCreateSearchVisitor(const vespalib::string & dir, const vdslib::Parameters & parameters);
    void testOnlyRequireWeakReadConsistency();

public:
    SearchVisitorTest();
    ~SearchVisitorTest() override;
    int Main() override;
};

SearchVisitorTest::SearchVisitorTest() :
    vespalib::TestApp(),
    _componentRegister(),
    _env(::config::ConfigUri("dir:" + TEST_PATH("cfg")))
{
    _componentRegister.setNodeInfo("mycluster", lib::NodeType::STORAGE, 1);
    _componentRegister.setClock(_clock);
    auto repo = std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig(TEST_PATH("cfg/documenttypes.cfg")));
    _componentRegister.setDocumentTypeRepo(repo);
    _component = std::make_unique<StorageComponent>(_componentRegister, "storage");
}

SearchVisitorTest::~SearchVisitorTest() = default;

Visitor::DocEntryList
createDocuments(const vespalib::string & dir)
{
    (void) dir;
    Visitor::DocEntryList documents;
    spi::Timestamp ts;
    auto e = spi::DocEntry::create(ts, std::make_unique<Document>());
    documents.push_back(std::move(e));
    return documents;
}

void
SearchVisitorTest::testCreateSearchVisitor(const vespalib::string & dir, const vdslib::Parameters & params)
{
    ::config::ConfigUri uri(dir);
    SearchVisitorFactory sFactory(uri);
    VisitorFactory & factory(sFactory);
    std::unique_ptr<Visitor> sv(static_cast<SearchVisitor *>(factory.makeVisitor(*_component, _env, params)));
    document::BucketId bucketId;
    Visitor::DocEntryList documents(createDocuments(dir));
    Visitor::HitCounter hitCounter;
    sv->handleDocuments(bucketId, documents, hitCounter);
}

void
SearchVisitorTest::testSearchEnvironment()
{
    EXPECT_TRUE(_env.getVSMAdapter("simple") != nullptr);
    EXPECT_TRUE(_env.getRankManager("simple") != nullptr);
}

void
SearchVisitorTest::testSearchVisitor()
{
    vdslib::Parameters params;
    params.set("searchcluster", "aaa");
    params.set("queryflags", "0x40000");
    params.set("summarycount", "3");
    params.set("summaryclass", "petra");
    params.set("rankprofile", "default");

    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addStringTerm("maptest", "sddocname", 0, Weight(0));
    Node::UP node = builder.build();
    vespalib::string stackDump = StackDumpCreator::create(*node);

    params.set("query", stackDump);
    testCreateSearchVisitor("dir:" + TEST_PATH("cfg"), params);
}

void
SearchVisitorTest::testOnlyRequireWeakReadConsistency()
{
    SearchVisitorFactory factory(::config::ConfigUri("dir:" + TEST_PATH("cfg")));
    VisitorFactory& factoryBase(factory);
    vdslib::Parameters params;
    std::unique_ptr<Visitor> sv(factoryBase.makeVisitor(*_component, _env, params));
    EXPECT_TRUE(sv->getRequiredReadConsistency() == spi::ReadConsistency::WEAK);
}

int
SearchVisitorTest::Main()
{
    TEST_INIT("searchvisitor_test");

    testSearchVisitor(); TEST_FLUSH();
    testSearchEnvironment(); TEST_FLUSH();
    testOnlyRequireWeakReadConsistency(); TEST_FLUSH();

    TEST_DONE();
}

} // namespace streaming

TEST_APPHOOK(::streaming::SearchVisitorTest)
