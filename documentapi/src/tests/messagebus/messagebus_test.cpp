// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;
using namespace documentapi;
using mbus::Blob;
using mbus::Routable;
using mbus::IRoutingPolicy;

class Test : public vespalib::TestApp {
    DocumentTypeRepo::SP _repo;

public:
    int Main() override;

private:
    void testMessage();
    void testProtocol();
};

TEST_APPHOOK(Test);

int
Test::Main()
{
    TEST_INIT(_argv[0]);
    _repo.reset(new DocumentTypeRepo(readDocumenttypesConfig(
            TEST_PATH("../../../test/cfg/testdoctypes.cfg"))));

    testMessage();  TEST_FLUSH();
    testProtocol(); TEST_FLUSH();

    TEST_DONE();
}

void Test::testMessage() {
    const document::DataType *testdoc_type = _repo->getDocumentType("testdoc");

    // Test one update.
    UpdateDocumentMessage upd1(
            document::DocumentUpdate::SP(
                    new document::DocumentUpdate(*testdoc_type,
                            document::DocumentId(document::DocIdString(
                                            "testdoc", "testme1")))));

    EXPECT_TRUE(upd1.getType() == DocumentProtocol::MESSAGE_UPDATEDOCUMENT);
    EXPECT_TRUE(upd1.getProtocol() == "document");

    LoadTypeSet set;
    DocumentProtocol protocol(set, _repo);

    Blob blob = protocol.encode(vespalib::Version(5,0), upd1);
    EXPECT_TRUE(blob.size() > 0);

    Routable::UP dec1 = protocol.decode(vespalib::Version(5,0), blob);
    EXPECT_TRUE(dec1.get() != NULL);
    EXPECT_TRUE(dec1->isReply() == false);
    EXPECT_TRUE(dec1->getType() == DocumentProtocol::MESSAGE_UPDATEDOCUMENT);

    // Compare to another.
    UpdateDocumentMessage upd2(
            document::DocumentUpdate::SP(
                    new document::DocumentUpdate(*testdoc_type,
                            document::DocumentId(document::DocIdString(
                                            "testdoc", "testme2")))));
    EXPECT_TRUE(!(upd1.getDocumentUpdate()->getId() == upd2.getDocumentUpdate()->getId()));

    DocumentMessage& msg2 = static_cast<DocumentMessage&>(upd2);
    EXPECT_TRUE(msg2.getType() == DocumentProtocol::MESSAGE_UPDATEDOCUMENT);
}

void Test::testProtocol() {
    LoadTypeSet set;
    DocumentProtocol protocol(set, _repo);
    EXPECT_TRUE(protocol.getName() == "document");

    IRoutingPolicy::UP policy = protocol.createPolicy(string("SearchRow"),string(""));
    EXPECT_TRUE(policy.get() != NULL);

    policy = protocol.createPolicy(string("SearchColumn"),string(""));
    EXPECT_TRUE(policy.get() != NULL);

    policy = protocol.createPolicy(string("DocumentRouteSelector"), string("file:documentrouteselectorpolicy.cfg"));
    EXPECT_TRUE(policy.get() != NULL);

    policy = protocol.createPolicy(string(""),string(""));
    EXPECT_TRUE(policy.get() == NULL);

    policy = protocol.createPolicy(string("Balle"),string(""));
    EXPECT_TRUE(policy.get() == NULL);
}


