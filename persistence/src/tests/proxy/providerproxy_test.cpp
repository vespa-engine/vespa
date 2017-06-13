// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for providerproxy.

#include "dummy_provider_factory.h"
#include "mockprovider.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/persistence/proxy/providerproxy.h>
#include <vespa/persistence/proxy/providerstub.h>
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/closure.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/config-stor-distribution.h>

using document::BucketId;
using document::DataType;
using document::DocumentTypeRepo;
using std::ostringstream;
using vespalib::Gate;
using vespalib::ThreadStackExecutor;
using vespalib::makeClosure;
using vespalib::makeTask;
using namespace storage::spi;
using namespace storage;

namespace {

const int port = 14863;
const string connect_spec = "tcp/localhost:14863";
LoadType defaultLoadType(0, "default");

void startServer(const DocumentTypeRepo *repo, Gate *gate) {
    DummyProviderFactory factory(MockProvider::UP(new MockProvider));
    ProviderStub stub(port, 8, *repo, factory);
    gate->await();
    EXPECT_TRUE(stub.hasClient());
}

TEST("require that client can start connecting before server is up") {
    const DocumentTypeRepo repo;
    Gate gate;
    ThreadStackExecutor executor(1, 65536);
    executor.execute(makeTask(makeClosure(startServer, &repo, &gate)));
    ProviderProxy proxy(connect_spec, repo);
    gate.countDown();
    executor.sync();
}

TEST("require that when the server goes down it causes permanent failure.") {
    const DocumentTypeRepo repo;
    DummyProviderFactory factory(MockProvider::UP(new MockProvider));
    ProviderStub::UP server(new ProviderStub(port, 8, repo, factory));
    ProviderProxy proxy(connect_spec, repo);
    server.reset(0);

    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));
    Result result = proxy.flush(bucket, context);
    EXPECT_EQUAL(Result::FATAL_ERROR, result.getErrorCode());
}

struct Fixture {
    MockProvider &mock_spi;
    DummyProviderFactory factory;
    DocumentTypeRepo repo;
    ProviderStub stub;
    ProviderProxy proxy;

    Fixture()
        : mock_spi(*(new MockProvider)),
          factory(PersistenceProvider::UP(&mock_spi)),
          repo(),
          stub(port, 8, repo, factory),
          proxy(connect_spec, repo) {}
};

TEST_F("require that client handles initialize", Fixture) {
    Result result = f.proxy.initialize();
    EXPECT_EQUAL(MockProvider::INITIALIZE, f.mock_spi.last_called);
}

TEST_F("require that client handles getPartitionStates", Fixture) {
    PartitionStateListResult result = f.proxy.getPartitionStates();
    EXPECT_EQUAL(MockProvider::GET_PARTITION_STATES, f.mock_spi.last_called);
    EXPECT_EQUAL(1u, result.getList().size());
}

TEST_F("require that client handles listBuckets", Fixture) {
    const PartitionId partition_id(42);

    BucketIdListResult result = f.proxy.listBuckets(partition_id);
    EXPECT_EQUAL(MockProvider::LIST_BUCKETS, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    ASSERT_EQUAL(1u, result.getList().size());
}

TEST_F("require that client handles setClusterState", Fixture) {
    lib::ClusterState s("version:1 storage:3 distributor:3");
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));
    ClusterState state(s, 0, d);

    Result result = f.proxy.setClusterState(state);
    EXPECT_EQUAL(MockProvider::SET_CLUSTER_STATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles setActiveState", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const BucketInfo::ActiveState bucket_state = BucketInfo::NOT_ACTIVE;

    Result result = f.proxy.setActiveState(bucket, bucket_state);
    EXPECT_EQUAL(MockProvider::SET_ACTIVE_STATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles getBucketInfo", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);

    BucketInfoResult result = f.proxy.getBucketInfo(bucket);
    EXPECT_EQUAL(MockProvider::GET_BUCKET_INFO, f.mock_spi.last_called);

    const BucketInfo& info(result.getBucketInfo());
    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(1u, info.getChecksum());
    EXPECT_EQUAL(2u, info.getDocumentCount());
    EXPECT_EQUAL(3u, info.getDocumentSize());
    EXPECT_EQUAL(bucket_id, info.getEntryCount());
    EXPECT_EQUAL(partition_id, info.getUsedSize());
    EXPECT_EQUAL(true, info.isReady());
    EXPECT_EQUAL(true, info.isActive());
}

TEST_F("require that client handles put", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const Timestamp timestamp(84);
    Document::SP doc(new Document());

    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));
    Result result = f.proxy.put(bucket, timestamp, doc, context);
    EXPECT_EQUAL(MockProvider::PUT, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles remove by id", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const Timestamp timestamp(84);
    const DocumentId id("doc:test:1");
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    RemoveResult result = f.proxy.remove(bucket, timestamp, id, context);
    EXPECT_EQUAL(MockProvider::REMOVE_BY_ID, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(true, result.wasFound());
}

TEST_F("require that client handles removeIfFound", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const Timestamp timestamp(84);
    const DocumentId id("doc:test:1");
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    RemoveResult result = f.proxy.removeIfFound(bucket, timestamp, id, context);
    EXPECT_EQUAL(MockProvider::REMOVE_IF_FOUND, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(true, result.wasFound());
}

TEST_F("require that client handles update", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const Timestamp timestamp(84);
    DocumentUpdate::SP update(new DocumentUpdate(*DataType::DOCUMENT, DocumentId("doc:test:1")));
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    UpdateResult result = f.proxy.update(bucket, timestamp, update, context);
    EXPECT_EQUAL(MockProvider::UPDATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(timestamp - 10, result.getExistingTimestamp());
}

TEST_F("require that client handles flush", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    Result result = f.proxy.flush(bucket, context);
    EXPECT_EQUAL(MockProvider::FLUSH, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles get", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);

    document::AllFields field_set;
    const DocumentId id("doc:test:1");
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    GetResult result = f.proxy.get(bucket, field_set, id, context);
    EXPECT_EQUAL(MockProvider::GET, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(6u, result.getTimestamp());
    ASSERT_TRUE(result.hasDocument());
    EXPECT_EQUAL(Document(), result.getDocument());
}

TEST_F("require that client handles createIterator", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const DocumentSelection doc_sel("docsel");
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    document::AllFields field_set;

    Selection selection(doc_sel);
    selection.setFromTimestamp(Timestamp(84));
    selection.setToTimestamp(Timestamp(126));

    CreateIteratorResult result =
        f.proxy.createIterator(bucket, field_set, selection,
                               NEWEST_DOCUMENT_ONLY, context);

    EXPECT_EQUAL(MockProvider::CREATE_ITERATOR, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(partition_id, result.getIteratorId());
}

TEST_F("require that client handles iterate", Fixture) {
    const IteratorId iterator_id(42);
    const uint64_t max_byte_size = 21;
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    IterateResult result = f.proxy.iterate(iterator_id, max_byte_size, context);
    EXPECT_EQUAL(MockProvider::ITERATE, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
    EXPECT_EQUAL(1u, result.getEntries().size());
    EXPECT_TRUE(result.isCompleted());
}

TEST_F("require that client handles destroyIterator", Fixture) {
    const IteratorId iterator_id(42);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    f.proxy.destroyIterator(iterator_id, context);
    EXPECT_EQUAL(MockProvider::DESTROY_ITERATOR, f.mock_spi.last_called);
}

TEST_F("require that client handles createBucket", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    f.proxy.createBucket(bucket, context);
    EXPECT_EQUAL(MockProvider::CREATE_BUCKET, f.mock_spi.last_called);
}

TEST_F("require that server accepts deleteBucket", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    f.proxy.deleteBucket(bucket, context);
    EXPECT_EQUAL(MockProvider::DELETE_BUCKET, f.mock_spi.last_called);
}

TEST_F("require that client handles getModifiedBuckets", Fixture) {
    BucketIdListResult modifiedBuckets = f.proxy.getModifiedBuckets();
    EXPECT_EQUAL(MockProvider::GET_MODIFIED_BUCKETS, f.mock_spi.last_called);

    EXPECT_EQUAL(2u, modifiedBuckets.getList().size());
}

TEST_F("require that client handles split", Fixture) {
    const uint64_t bucket_id_1 = 21;
    const PartitionId partition_id_1(42);
    const Bucket bucket_1(BucketId(bucket_id_1), partition_id_1);
    const uint64_t bucket_id_2 = 210;
    const PartitionId partition_id_2(420);
    const Bucket bucket_2(BucketId(bucket_id_2), partition_id_2);
    const uint64_t bucket_id_3 = 2100;
    const PartitionId partition_id_3(4200);
    const Bucket bucket_3(BucketId(bucket_id_3), partition_id_3);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    Result result = f.proxy.split(bucket_1, bucket_2, bucket_3, context);
    EXPECT_EQUAL(MockProvider::SPLIT, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles join", Fixture) {
    const uint64_t bucket_id_1 = 21;
    const PartitionId partition_id_1(42);
    const Bucket bucket_1(BucketId(bucket_id_1), partition_id_1);
    const uint64_t bucket_id_2 = 210;
    const PartitionId partition_id_2(420);
    const Bucket bucket_2(BucketId(bucket_id_2), partition_id_2);
    const uint64_t bucket_id_3 = 2100;
    const PartitionId partition_id_3(4200);
    const Bucket bucket_3(BucketId(bucket_id_3), partition_id_3);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    Result result = f.proxy.join(bucket_1, bucket_2, bucket_3, context);
    EXPECT_EQUAL(MockProvider::JOIN, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles move", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId from_partition_id(42);
    const PartitionId to_partition_id(43);
    const Bucket bucket(BucketId(bucket_id), from_partition_id);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    Result result = f.proxy.move(bucket, to_partition_id, context);
    EXPECT_EQUAL(MockProvider::MOVE, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles maintain", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);

    Result result = f.proxy.maintain(bucket, HIGH);
    EXPECT_EQUAL(MockProvider::MAINTAIN, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

TEST_F("require that client handles remove entry", Fixture) {
    const uint64_t bucket_id = 21;
    const PartitionId partition_id(42);
    const Bucket bucket(BucketId(bucket_id), partition_id);
    const Timestamp timestamp(345);
    Context context(defaultLoadType, Priority(0), Trace::TraceLevel(0));

    Result result = f.proxy.removeEntry(bucket, timestamp, context);
    EXPECT_EQUAL(MockProvider::REMOVE_ENTRY, f.mock_spi.last_called);

    EXPECT_EQUAL(0, result.getErrorCode());
    EXPECT_EQUAL("", result.getErrorMessage());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
