// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

using namespace search;
using namespace search::query;
using namespace document;

namespace storage {

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
    ~SearchVisitorTest();
    int Main() override;
};

SearchVisitorTest::SearchVisitorTest() :
    vespalib::TestApp(),
    _componentRegister(),
    _env("dir:" + TEST_PATH("cfg"))
{
    _componentRegister.setNodeInfo("mycluster", lib::NodeType::STORAGE, 1);
    _componentRegister.setClock(_clock);
    StorageComponent::DocumentTypeRepoSP repo(new DocumentTypeRepo(readDocumenttypesConfig(TEST_PATH("cfg/documenttypes.cfg"))));
    _componentRegister.setDocumentTypeRepo(repo);
    _component.reset(new StorageComponent(_componentRegister, "storage"));
}

SearchVisitorTest::~SearchVisitorTest() {}

std::vector<spi::DocEntry::UP>
createDocuments(const vespalib::string & dir)
{
    (void) dir;
    std::vector<spi::DocEntry::UP> documents;
    spi::Timestamp ts;
    document::Document::UP doc(new document::Document());
    spi::DocEntry::UP e(new spi::DocEntry(ts, 0, std::move(doc)));
    documents.push_back(std::move(e));
    return documents;
}

void
SearchVisitorTest::testCreateSearchVisitor(const vespalib::string & dir, const vdslib::Parameters & params)
{
    SearchVisitorFactory sFactory(dir);
    VisitorFactory & factory(sFactory);
    std::unique_ptr<Visitor> sv(static_cast<SearchVisitor *>(factory.makeVisitor(*_component, _env, params)));
    document::OrderingSpecification orderSpec;
    document::BucketId bucketId;
    std::vector<spi::DocEntry::UP> documents(createDocuments(dir));
    Visitor::HitCounter hitCounter(&orderSpec);
    sv->handleDocuments(bucketId, documents, hitCounter);
}

void
SearchVisitorTest::testSearchEnvironment()
{
    EXPECT_TRUE(_env.getVSMAdapter("simple") != NULL);
    EXPECT_TRUE(_env.getRankManager("simple") != NULL);
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
    SearchVisitorFactory factory("dir:" + TEST_PATH("cfg"));
    VisitorFactory& factoryBase(factory);
    vdslib::Parameters params;
    std::unique_ptr<Visitor> sv(
            factoryBase.makeVisitor(*_component, _env, params));
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

} // namespace storage

TEST_APPHOOK(storage::SearchVisitorTest)
