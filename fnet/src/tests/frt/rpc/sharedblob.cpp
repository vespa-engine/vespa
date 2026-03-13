// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vector>

constexpr size_t ALLOC_LIMIT = 1024;

struct MyBlob : FRT_ISharedBlob {
    int refcnt;
    MyBlob() : refcnt(1) {}
    uint32_t    getLen() override { return (strlen("blob_test") + 1); }
    const char* getData() override { return "blob_test"; }
    void        addRef() override { ++refcnt; }
    void        subRef() override { --refcnt; }
};

struct Data {
    enum { SMALL = (ALLOC_LIMIT / 2), LARGE = (ALLOC_LIMIT * 2) };

    char*    buf;
    uint32_t len;

    Data(const char* pt, uint32_t l) : buf(new char[l]), len(l) {
        if (len > 0) {
            memcpy(buf, pt, len);
        }
    }
    Data(uint32_t l, char c) : buf(new char[l]), len(l) {
        if (len > 0) {
            memset(buf, c, len);
        }
    }
    Data(const Data& rhs) : buf(new char[rhs.len]), len(rhs.len) {
        if (len > 0) {
            memcpy(buf, rhs.buf, len);
        }
    }
    Data& operator=(const Data& rhs) {
        if (this != &rhs) {
            delete[] buf;
            buf = new char[rhs.len];
            len = rhs.len;
            if (len > 0) {
                memcpy(buf, rhs.buf, len);
            }
        }
        return *this;
    }
    bool check(uint32_t l, char c) {
        if (l != len) {
            fprintf(stderr, "blob length was %u, expected %u\n", len, l);
            return false;
        }
        for (uint32_t i = 0; i < l; ++i) {
            if (buf[i] != c) {
                fprintf(stderr, "byte at offset %u was %c, expected %c\n", i, buf[i], c);
                return false;
            }
        }
        return true;
    }
    ~Data() { delete[] buf; }
};

struct DataSet {
    std::vector<Data> blobs;

    void sample(FRT_Values& v) {
        blobs.push_back(Data(v.GetNumValues(), 'V'));
        for (uint32_t i = 0; i < v.GetNumValues(); ++i) {
            if (v.GetType(i) == FRT_VALUE_DATA) {
                blobs.push_back(Data(1, 'x'));
                blobs.push_back(Data(v[i]._data._buf, v[i]._data._len));
            } else if (v.GetType(i) == FRT_VALUE_DATA_ARRAY) {
                blobs.push_back(Data(v[i]._data_array._len, 'X'));
                for (uint32_t j = 0; j < v[i]._data_array._len; ++j) {
                    blobs.push_back(Data(v[i]._data_array._pt[j]._buf, v[i]._data_array._pt[j]._len));
                }
            }
        }
    }
};

struct ServerSampler : public FRT_Invokable {
    DataSet&        dataSet;
    FRT_RPCRequest* clientReq;
    FRT_RPCRequest* serverReq;

    ServerSampler(DataSet& ds, FRT_RPCRequest* cr) : dataSet(ds), clientReq(cr), serverReq(nullptr) {}

    void RPC_test(FRT_RPCRequest* req) {
        if (clientReq != nullptr) {
            dataSet.sample(*clientReq->GetParams()); // client params after drop
        }

        // store away parameters
        FNET_DataBuffer buf;
        buf.EnsureFree(req->GetParams()->GetLength());
        req->GetParams()->EncodeCopy(&buf);

        dataSet.sample(*req->GetParams()); // server params before drop
        req->DiscardBlobs();
        dataSet.sample(*req->GetParams()); // server params after drop

        // restore parameters into return values
        req->GetReturn()->DecodeCopy(&buf, buf.GetDataLen());

        dataSet.sample(*req->GetReturn()); // server return before drop

        // keep request to sample return after drop
        req->internal_addref();
        serverReq = req;
    }
};

TEST(SharedBlobTest, testExplicitShared) {
    fnet::frt::StandaloneFRT frt;
    FRT_Supervisor&          orb = frt.supervisor();
    MyBlob                   blob;

    FRT_RPCRequest* req = orb.AllocRPCRequest();
    EXPECT_TRUE(blob.refcnt == 1);

    req->GetParams()->AddSharedData(&blob);
    req->GetParams()->AddInt32(42);
    req->GetParams()->AddSharedData(&blob);
    req->GetParams()->AddInt32(84);
    req->GetParams()->AddSharedData(&blob);

    EXPECT_EQ(4, blob.refcnt);
    EXPECT_TRUE(strcmp(req->GetParamSpec(), "xixix") == 0);
    EXPECT_TRUE(req->GetParams()->GetValue(0)._data._len == blob.getLen());
    EXPECT_TRUE(req->GetParams()->GetValue(0)._data._buf == blob.getData());
    EXPECT_TRUE(req->GetParams()->GetValue(1)._intval32 == 42);
    EXPECT_TRUE(req->GetParams()->GetValue(2)._data._len == blob.getLen());
    EXPECT_TRUE(req->GetParams()->GetValue(2)._data._buf == blob.getData());
    EXPECT_TRUE(req->GetParams()->GetValue(3)._intval32 == 84);
    EXPECT_TRUE(req->GetParams()->GetValue(4)._data._len == blob.getLen());
    EXPECT_TRUE(req->GetParams()->GetValue(4)._data._buf == blob.getData());

    req->CreateRequestPacket(true)->Free(); // fake request send.

    EXPECT_EQ(1, blob.refcnt);
    EXPECT_TRUE(strcmp(req->GetParamSpec(), "xixix") == 0);
    EXPECT_TRUE(req->GetParams()->GetValue(0)._data._len == 0);
    EXPECT_TRUE(req->GetParams()->GetValue(0)._data._buf == nullptr);
    EXPECT_TRUE(req->GetParams()->GetValue(1)._intval32 == 42);
    EXPECT_TRUE(req->GetParams()->GetValue(2)._data._len == 0);
    EXPECT_TRUE(req->GetParams()->GetValue(2)._data._buf == nullptr);
    EXPECT_TRUE(req->GetParams()->GetValue(3)._intval32 == 84);
    EXPECT_TRUE(req->GetParams()->GetValue(4)._data._len == 0);
    EXPECT_TRUE(req->GetParams()->GetValue(4)._data._buf == nullptr);

    EXPECT_EQ(1, blob.refcnt);
    req = orb.AllocRPCRequest(req);
    EXPECT_EQ(1, blob.refcnt);

    req->GetParams()->AddSharedData(&blob);
    req->GetParams()->AddInt32(42);
    req->GetParams()->AddSharedData(&blob);
    req->GetParams()->AddInt32(84);
    req->GetParams()->AddSharedData(&blob);

    EXPECT_EQ(4, blob.refcnt);
    req->internal_subref();
    EXPECT_EQ(1, blob.refcnt);
}

TEST(SharedBlobTest, testImplicitShared) {
    DataSet                  dataSet;
    fnet::frt::StandaloneFRT frt;
    FRT_Supervisor&          orb = frt.supervisor();
    FRT_RPCRequest*          req = orb.AllocRPCRequest();
    ServerSampler            serverSampler(dataSet, req);
    {
        FRT_ReflectionBuilder rb(&orb);
        rb.DefineMethod("test", "*", "*", FRT_METHOD(ServerSampler::RPC_test), &serverSampler);
    }
    orb.Listen(0);
    int port = orb.GetListenPort();
    ASSERT_TRUE(port != 0);

    char tmp[64];
    snprintf(tmp, sizeof(tmp), "tcp/localhost:%d", port);
    FRT_Target* target = orb.GetTarget(tmp);
    req->SetMethodName("test");
    {
        Data data(Data::SMALL, 'a');
        req->GetParams()->AddData(data.buf, data.len);
    }
    {
        Data data(Data::LARGE, 'b');
        req->GetParams()->AddData(data.buf, data.len);
    }
    {
        char* data = req->GetParams()->AddData(Data::LARGE);
        memset(data, 'c', Data::LARGE);
    }
    {
        Data           data1(Data::SMALL, 'd');
        Data           data2(Data::LARGE, 'e');
        FRT_DataValue* arr = req->GetParams()->AddDataArray(2);
        req->GetParams()->SetData(&arr[0], data1.buf, data1.len);
        req->GetParams()->SetData(&arr[1], data2.buf, data2.len);
    }

    dataSet.sample(*req->GetParams()); // client params before drop

    target->InvokeSync(req, 30.0);

    if (serverSampler.serverReq != nullptr) {
        dataSet.sample(*serverSampler.serverReq->GetReturn()); // server return after drop
    }
    dataSet.sample(*req->GetReturn()); // client return before drop

    req->DiscardBlobs();

    dataSet.sample(*req->GetReturn()); // client return after drop

    // verify blob samples
    EXPECT_EQ(dataSet.blobs.size(), 80u);

    for (int i = 0; i < 80; i += 20) {
        // before discard (client params, server params, server return, client return)
        EXPECT_TRUE(dataSet.blobs[i + 0].check(4, 'V'));
        EXPECT_TRUE(dataSet.blobs[i + 1].check(1, 'x'));
        EXPECT_TRUE(dataSet.blobs[i + 2].check(Data::SMALL, 'a'));
        EXPECT_TRUE(dataSet.blobs[i + 3].check(1, 'x'));
        EXPECT_TRUE(dataSet.blobs[i + 4].check(Data::LARGE, 'b'));
        EXPECT_TRUE(dataSet.blobs[i + 5].check(1, 'x'));
        EXPECT_TRUE(dataSet.blobs[i + 6].check(Data::LARGE, 'c'));
        EXPECT_TRUE(dataSet.blobs[i + 7].check(2, 'X'));
        EXPECT_TRUE(dataSet.blobs[i + 8].check(Data::SMALL, 'd'));
        EXPECT_TRUE(dataSet.blobs[i + 9].check(Data::LARGE, 'e'));

        // after discard (client params, server params, server return, client return)
        EXPECT_TRUE(dataSet.blobs[i + 10].check(4, 'V'));
        EXPECT_TRUE(dataSet.blobs[i + 11].check(1, 'x'));
        EXPECT_TRUE(dataSet.blobs[i + 12].check(Data::SMALL, 'a'));
        EXPECT_TRUE(dataSet.blobs[i + 13].check(1, 'x'));
        EXPECT_TRUE(dataSet.blobs[i + 14].check(0, 0));
        EXPECT_TRUE(dataSet.blobs[i + 15].check(1, 'x'));
        EXPECT_TRUE(dataSet.blobs[i + 16].check(0, 0));
        EXPECT_TRUE(dataSet.blobs[i + 17].check(2, 'X'));
        EXPECT_TRUE(dataSet.blobs[i + 18].check(Data::SMALL, 'd'));
        EXPECT_TRUE(dataSet.blobs[i + 19].check(0, 0));
    }

    if (serverSampler.serverReq != nullptr) {
        serverSampler.serverReq->internal_subref();
    }
    req->internal_subref();
    target->internal_subref();
}

GTEST_MAIN_RUN_ALL_TESTS()
