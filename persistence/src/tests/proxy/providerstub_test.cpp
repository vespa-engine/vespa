// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for providerstub.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/persistence/proxy/buildid.h>
#include <vespa/persistence/proxy/providerstub.h>
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/target.h>

using document::BucketId;
using document::ByteBuffer;
using document::DataType;
using document::DocumentTypeRepo;
using document::VespaDocumentSerializer;
using vespalib::nbostream;
using namespace storage::spi;
using namespace storage;

#include <tests/proxy/mockprovider.h>
#include "dummy_provider_factory.h"

namespace {

const int port = 14863;
const char connect_spec[] = "tcp/localhost:14863";
const string build_id = getBuildId();

struct Fixture {
    MockProvider &mock_spi;
    DummyProviderFactory factory;
    DocumentTypeRepo repo;
    ProviderStub stub;
    FRT_Supervisor supervisor;
    FRT_RPCRequest *current_request;
    FRT_Target *target;

    Fixture()
        : mock_spi(*(new MockProvider())),
          factory(PersistenceProvider::UP(&mock_spi)),
          repo(),
          stub(port, 8, repo, factory),
          supervisor(),
          current_request(0),
          target(supervisor.GetTarget(connect_spec))
    {
        supervisor.Start();
        ASSERT_TRUE(target);
    }
    ~Fixture() {
        if (current_request) {
            current_request->SubRef();
        }
        target->SubRef();
        supervisor.ShutDown(true);
    }
    FRT_RPCRequest *getRequest(const string &name) {
        FRT_RPCRequest *req = supervisor.AllocRPCRequest(current_request);
        current_request = req;
        req->SetMethodName(name.c_str());
        return req;
    }
    void callRpc(FRT_RPCRequest *req, const string &return_spec) {
        target->InvokeSync(req, 5.0);
        req->CheckReturnTypes(return_spec.c_str());
        if (!EXPECT_EQUAL(uint32_t(FRTE_NO_ERROR), req->GetErrorCode())) {
            TEST_FATAL(req->GetErrorMessage());
        }
    }
    void failRpc(FRT_RPCRequest *req, uint32_t error_code) {
        target->InvokeSync(req, 5.0);
        EXPECT_EQUAL(error_code, req->GetErrorCode());
    }
};

struct ConnectedFixture : Fixture {
    ConnectedFixture() {
        FRT_RPCRequest *req = getRequest("vespa.persistence.connect");
        req->GetParams()->AddString(build_id.data(), build_id.size());
        callRpc(req, "");
    }
};

TEST("print build id") { fprintf(stderr, "build id: '%s'\n", getBuildId()); }

TEST_F("require that server accepts connect", Fixture) {
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.connect");
    req->GetParams()->AddString(build_id.data(), build_id.size());
    f.callRpc(req, "");
    EXPECT_TRUE(f.stub.hasClient());
}

TEST_F("require that connect can be called twice", ConnectedFixture) {
    EXPECT_TRUE(f.stub.hasClient());
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.connect");
    req->GetParams()->AddString(build_id.data(), build_id.size());
    f.callRpc(req, "");
    EXPECT_TRUE(f.stub.hasClient());
}

TEST_F("require that connect fails with wrong build id", Fixture) {
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.connect");
    const string wrong_id = "wrong build id";
    req->GetParams()->AddString(wrong_id.data(), wrong_id.size());
    f.failRpc(req, FRTE_RPC_METHOD_FAILED);
    string prefix("Wrong build id. Got 'wrong build id', required ");
    EXPECT_EQUAL(prefix,
                 string(req->GetErrorMessage()).substr(0, prefix.size()));
    EXPECT_FALSE(f.stub.hasClient());
}

TEST_F("require that only one client can connect", ConnectedFixture) {
    EXPECT_TRUE(f.stub.hasClient());
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.connect");
    req->GetParams()->AddString(build_id.data(), build_id.size());
    FRT_Target *target = f.supervisor.GetTarget(connect_spec);
    target->InvokeSync(req, 5.0);
    target->SubRef();
    EXPECT_EQUAL(uint32_t(FRTE_RPC_METHOD_FAILED), req->GetErrorCode());
    EXPECT_EQUAL("Server is already connected",
                 string(req->GetErrorMessage()));
}

TEST_F("require that server accepts getPartitionStates", ConnectedFixture) {
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.getPartitionStates");
    f.callRpc(req, "bsIS");
    EXPECT_EQUAL(MockProvider::GET_PARTITION_STATES, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(2)._int32_array._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(3)._string_array._len);
}

TEST_F("require that server accepts listBuckets", ConnectedFixture) {
    const uint64_t partition_id = 42;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.listBuckets");
    req->GetParams()->AddInt64(partition_id);
    f.callRpc(req, "bsL");
    EXPECT_EQUAL(MockProvider::LIST_BUCKETS, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(2)._int64_array._len);
    EXPECT_EQUAL(partition_id,
                 req->GetReturn()->GetValue(2)._int64_array._pt[0]);
}

TEST_F("require that server accepts setClusterState", ConnectedFixture) {
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.setClusterState");

    lib::ClusterState s("version:1 storage:3 distributor:3");
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));
    ClusterState state(s, 0, d);
    vespalib::nbostream o;
    state.serialize(o);
    req->GetParams()->AddData(o.c_str(), o.size());
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::SET_CLUSTER_STATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts setActiveState", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const BucketInfo::ActiveState bucket_state = BucketInfo::NOT_ACTIVE;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.setActiveState");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddInt8(bucket_state);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::SET_ACTIVE_STATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts getBucketInfo", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.getBucketInfo");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    f.callRpc(req, "bsiiiiibb");
    EXPECT_EQUAL(MockProvider::GET_BUCKET_INFO, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(2)._intval32);
    EXPECT_EQUAL(2u, req->GetReturn()->GetValue(3)._intval32);
    EXPECT_EQUAL(3u, req->GetReturn()->GetValue(4)._intval32);
    EXPECT_EQUAL(bucket_id, req->GetReturn()->GetValue(5)._intval32);
    EXPECT_EQUAL(partition_id, req->GetReturn()->GetValue(6)._intval32);
    EXPECT_EQUAL(static_cast<uint8_t>(BucketInfo::READY),
                 req->GetReturn()->GetValue(7)._intval8);
    EXPECT_EQUAL(static_cast<uint8_t>(BucketInfo::ACTIVE),
                 req->GetReturn()->GetValue(8)._intval8);
}

TEST_F("require that server accepts put", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const Timestamp timestamp(84);
    Document::UP doc(new Document);
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(*doc, document::COMPLETE);

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.put");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddInt64(timestamp);
    req->GetParams()->AddData(stream.c_str(), stream.size());
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::PUT, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

void testRemove(ConnectedFixture &f, const string &rpc_name,
                MockProvider::Function func) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const Timestamp timestamp(84);
    const DocumentId id("doc:test:1");

    FRT_RPCRequest *req = f.getRequest(rpc_name);
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddInt64(timestamp);
    req->GetParams()->AddString(id.toString().data(), id.toString().size());
    f.callRpc(req, "bsb");
    EXPECT_EQUAL(func, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_TRUE(req->GetReturn()->GetValue(2)._intval8);
}

TEST_F("require that server accepts remove by id", ConnectedFixture) {
    testRemove(f, "vespa.persistence.removeById", MockProvider::REMOVE_BY_ID);
}

TEST_F("require that server accepts removeIfFound", ConnectedFixture) {
    testRemove(f, "vespa.persistence.removeIfFound",
               MockProvider::REMOVE_IF_FOUND);
}

TEST_F("require that server accepts update", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const Timestamp timestamp(84);
    DocumentUpdate update(*DataType::DOCUMENT, DocumentId("doc:test:1"));
    vespalib::nbostream stream;
    update.serializeHEAD(stream);

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.update");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddInt64(timestamp);
    req->GetParams()->AddData(stream.c_str(), stream.size());
    f.callRpc(req, "bsl");
    EXPECT_EQUAL(MockProvider::UPDATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(timestamp - 10, req->GetReturn()->GetValue(2)._intval64);
}

TEST_F("require that server accepts flush", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.flush");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::FLUSH, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts get", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const string field_set_1 = "[all]";
    const DocumentId id("doc:test:1");

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.get");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddString(field_set_1.data(), field_set_1.size());
    req->GetParams()->AddString(id.toString().data(), id.toString().size());
    f.callRpc(req, "bslx");
    EXPECT_EQUAL(MockProvider::GET, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(6u, req->GetReturn()->GetValue(2)._intval64);
    EXPECT_EQUAL(25u, req->GetReturn()->GetValue(3)._data._len);
}

TEST_F("require that server accepts createIterator", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const string doc_sel = "docsel";
    const Timestamp timestamp_from(84);
    const Timestamp timestamp_to(126);
    const Timestamp timestamp_subset(168);
    const string field_set_1 = "[all]";
    const bool include_removes = false;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.createIterator");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddString(field_set_1.data(), field_set_1.size());
    req->GetParams()->AddString(doc_sel.data(), doc_sel.size());
    req->GetParams()->AddInt64(timestamp_from);
    req->GetParams()->AddInt64(timestamp_to);
    req->GetParams()->AddInt64Array(1)[0] = timestamp_subset;
    req->GetParams()->AddInt8(include_removes);

    f.callRpc(req, "bsl");
    EXPECT_EQUAL(MockProvider::CREATE_ITERATOR, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(partition_id, req->GetReturn()->GetValue(2)._intval64);
}

TEST_F("require that server accepts iterate", ConnectedFixture) {
    const uint64_t iterator_id = 42;
    const uint64_t max_byte_size = 21;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.iterate");
    req->GetParams()->AddInt64(iterator_id);
    req->GetParams()->AddInt64(max_byte_size);
    f.callRpc(req, "bsLISXb");
    EXPECT_EQUAL(MockProvider::ITERATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(2)._int64_array._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(3)._int32_array._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(4)._string_array._len);
    EXPECT_EQUAL(1u, req->GetReturn()->GetValue(5)._data_array._len);
    EXPECT_TRUE(req->GetReturn()->GetValue(6)._intval8);
}

TEST_F("require that server accepts destroyIterator", ConnectedFixture) {
    const uint64_t iterator_id = 42;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.destroyIterator");
    req->GetParams()->AddInt64(iterator_id);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::DESTROY_ITERATOR, f.mock_spi.last_called);
}

TEST_F("require that server accepts createBucket", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.createBucket");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::CREATE_BUCKET, f.mock_spi.last_called);
}

TEST_F("require that server accepts deleteBucket", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.deleteBucket");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::DELETE_BUCKET, f.mock_spi.last_called);
}

TEST_F("require that server accepts getModifiedBuckets", ConnectedFixture) {
    FRT_RPCRequest *req = f.getRequest("vespa.persistence.getModifiedBuckets");
    f.callRpc(req, "bsL");
    EXPECT_EQUAL(MockProvider::GET_MODIFIED_BUCKETS, f.mock_spi.last_called);
    EXPECT_EQUAL(2u, req->GetReturn()->GetValue(2)._int64_array._len);
}

TEST_F("require that server accepts split", ConnectedFixture) {
    const uint64_t bucket_id_1 = 21;
    const uint64_t partition_id_1 = 42;
    const uint64_t bucket_id_2 = 210;
    const uint64_t partition_id_2 = 420;
    const uint64_t bucket_id_3 = 2100;
    const uint64_t partition_id_3 = 4200;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.split");
    req->GetParams()->AddInt64(bucket_id_1);
    req->GetParams()->AddInt64(partition_id_1);
    req->GetParams()->AddInt64(bucket_id_2);
    req->GetParams()->AddInt64(partition_id_2);
    req->GetParams()->AddInt64(bucket_id_3);
    req->GetParams()->AddInt64(partition_id_3);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::SPLIT, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts join", ConnectedFixture) {
    const uint64_t bucket_id_1 = 21;
    const uint64_t partition_id_1 = 42;
    const uint64_t bucket_id_2 = 210;
    const uint64_t partition_id_2 = 420;
    const uint64_t bucket_id_3 = 2100;
    const uint64_t partition_id_3 = 4200;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.join");
    req->GetParams()->AddInt64(bucket_id_1);
    req->GetParams()->AddInt64(partition_id_1);
    req->GetParams()->AddInt64(bucket_id_2);
    req->GetParams()->AddInt64(partition_id_2);
    req->GetParams()->AddInt64(bucket_id_3);
    req->GetParams()->AddInt64(partition_id_3);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::JOIN, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts move", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t from_partition_id = 42;
    const uint64_t to_partition_id = 43;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.move");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(from_partition_id);
    req->GetParams()->AddInt64(to_partition_id);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::MOVE, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts maintain", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const MaintenanceLevel verification_level = HIGH;

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.maintain");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddInt8(verification_level);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::MAINTAIN, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

TEST_F("require that server accepts remove_entry", ConnectedFixture) {
    const uint64_t bucket_id = 21;
    const uint64_t partition_id = 42;
    const Timestamp timestamp(345);

    FRT_RPCRequest *req = f.getRequest("vespa.persistence.removeEntry");
    req->GetParams()->AddInt64(bucket_id);
    req->GetParams()->AddInt64(partition_id);
    req->GetParams()->AddInt64(timestamp);
    f.callRpc(req, "bs");
    EXPECT_EQUAL(MockProvider::REMOVE_ENTRY, f.mock_spi.last_called);

    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(0)._intval8);
    EXPECT_EQUAL(0u, req->GetReturn()->GetValue(1)._string._len);
}

void checkRpcFails(const string &name, const string &param_spec, Fixture &f) {
    TEST_STATE(name.c_str());
    FRT_RPCRequest *req = f.getRequest("vespa.persistence." + name);
    for (size_t i = 0; i < param_spec.size(); ++i) {
        switch(param_spec[i]) {
        case 'b': req->GetParams()->AddInt8(0); break;
        case 'l': req->GetParams()->AddInt64(0); break;
        case 'L': req->GetParams()->AddInt64Array(0); break;
        case 's': req->GetParams()->AddString(0, 0); break;
        case 'S': req->GetParams()->AddStringArray(0); break;
        case 'x': req->GetParams()->AddData(0, 0); break;
        }
    }
    f.failRpc(req, FRTE_RPC_METHOD_FAILED);
}

TEST_F("require that unconnected server fails all SPI calls.", Fixture)
{
    checkRpcFails("initialize", "", f);
    checkRpcFails("getPartitionStates", "", f);
    checkRpcFails("listBuckets", "l", f);
    checkRpcFails("setClusterState", "x", f);
    checkRpcFails("setActiveState", "llb", f);
    checkRpcFails("getBucketInfo", "ll", f);
    checkRpcFails("put", "lllx", f);
    checkRpcFails("removeById", "llls", f);
    checkRpcFails("removeIfFound", "llls", f);
    checkRpcFails("update", "lllx", f);
    checkRpcFails("flush", "ll", f);
    checkRpcFails("get", "llss", f);
    checkRpcFails("createIterator", "llssllLb", f);
    checkRpcFails("iterate", "ll", f);
    checkRpcFails("destroyIterator", "l", f);
    checkRpcFails("createBucket", "ll", f);
    checkRpcFails("deleteBucket", "ll", f);
    checkRpcFails("getModifiedBuckets", "", f);
    checkRpcFails("split", "llllll", f);
    checkRpcFails("join", "llllll", f);
    checkRpcFails("maintain", "llb", f);
    checkRpcFails("removeEntry", "lll", f);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
