// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <logd/exceptions.h>
#include <logd/rpc_forwarder.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace logdemon;

void
encode_log_response(const ProtoConverter::ProtoLogResponse& src, FRT_Values& dst)
{
    auto buf = src.SerializeAsString();
    dst.AddInt8(0);
    dst.AddInt32(buf.size());
    dst.AddData(buf.data(), buf.size());
}

bool
decode_log_request(FRT_Values& src, ProtoConverter::ProtoLogRequest& dst)
{
    uint8_t encoding = src[0]._intval8;
    assert(encoding == 0);
    uint32_t uncompressed_size = src[1]._intval32;
    assert(uncompressed_size == src[2]._data._len);
    return dst.ParseFromArray(src[2]._data._buf, src[2]._data._len);
}

std::string garbage("garbage");

struct RpcServer : public FRT_Invokable {
    FRT_Supervisor supervisor;
    int request_count;
    std::vector<std::string> messages;
    bool reply_with_error;
    bool reply_with_proto_response;

public:
    RpcServer()
        : supervisor(),
          request_count(0),
          messages(),
          reply_with_error(false),
          reply_with_proto_response(true)
    {
        supervisor.Listen(0);
        supervisor.Start();
        FRT_ReflectionBuilder builder(&supervisor);
        builder.DefineMethod("vespa.logserver.archiveLogMessages", "bix", "bix",
                             FRT_METHOD(RpcServer::rpc_archive_log_messages), this);
    }
    ~RpcServer() {
        supervisor.ShutDown(true);
    }
    int get_listen_port() {
        return supervisor.GetListenPort();
    }
    void rpc_archive_log_messages(FRT_RPCRequest* request) {
        ProtoConverter::ProtoLogRequest proto_request;
        ASSERT_TRUE(decode_log_request(*request->GetParams(), proto_request));
        ++request_count;
        for (const auto& message : proto_request.log_messages()) {
            messages.push_back(message.payload());
        }
        if (reply_with_error) {
            request->SetError(123, "This is a server error");
            return;
        }
        if (reply_with_proto_response) {
            ProtoConverter::ProtoLogResponse proto_response;
            encode_log_response(proto_response, *request->GetReturn());
        } else {
            auto& dst = *request->GetReturn();
            dst.AddInt8(0);
            dst.AddInt32(garbage.size());
            dst.AddData(garbage.data(), garbage.size());
        }
    }
};

std::string
make_log_line(const std::string& payload)
{
    return "1234.5678\tmy_host\t10/20\tmy_service\tmy_component\tinfo\t" + payload;
}

struct RpcForwarderTest : public ::testing::Test {
    RpcServer server;
    RpcForwarder forwarder;
    RpcForwarderTest()
        : forwarder("localhost", server.get_listen_port(), 60.0, 3)
    {
    }
    void forward_line(const std::string& payload) {
        forwarder.forwardLine(make_log_line(payload));
    }
    void forward_bad_line() {
        forwarder.forwardLine("badline");
    }
    void flush() {
        forwarder.flush();
    }
    void expect_messages() {
        expect_messages(0, {});
    }
    void expect_messages(int exp_request_count, const std::vector<std::string>& exp_messages) {
        EXPECT_EQ(exp_request_count, server.request_count);
        EXPECT_EQ(exp_messages, server.messages);
    }
};

TEST_F(RpcForwarderTest, does_not_send_rpc_with_no_log_messages)
{
    expect_messages();
    flush();
    expect_messages();
}

TEST_F(RpcForwarderTest, can_send_rpc_with_single_log_message)
{
    forward_line("a");
    expect_messages();
    flush();
    expect_messages(1, {"a"});
}

TEST_F(RpcForwarderTest, can_send_rpc_with_multiple_log_messages)
{
    forward_line("a");
    forward_line("b");
    expect_messages();
    flush();
    expect_messages(1, {"a", "b"});
}

TEST_F(RpcForwarderTest, automatically_sends_rpc_when_max_messages_limit_is_reached)
{
    forward_line("a");
    forward_line("b");
    expect_messages();
    forward_line("c");
    expect_messages(1, {"a", "b", "c"});
    forward_line("d");
    expect_messages(1, {"a", "b", "c"});
    forward_line("e");
    expect_messages(1, {"a", "b", "c"});
    forward_line("f");
    expect_messages(2, {"a", "b", "c", "d", "e", "f"});
}

TEST_F(RpcForwarderTest, bad_log_lines_are_counted_but_not_sent)
{
    forward_line("a");
    forward_bad_line();
    EXPECT_EQ(1, forwarder.badLines());
    flush();
    expect_messages(1, {"a"});
}

TEST_F(RpcForwarderTest, bad_log_lines_count_can_be_reset)
{
    forward_bad_line();
    EXPECT_EQ(1, forwarder.badLines());
    forwarder.resetBadLines();
    EXPECT_EQ(0, forwarder.badLines());
}

TEST_F(RpcForwarderTest, throws_when_rpc_reply_contains_errors)
{
    server.reply_with_error = true;
    forward_line("a");
    EXPECT_THROW(flush(), logdemon::ConnectionException);
}

TEST_F(RpcForwarderTest, throws_when_rpc_reply_does_not_contain_proto_response)
{
    server.reply_with_proto_response = false;
    forward_line("a");
    EXPECT_THROW(flush(), logdemon::DecodeException);
}

GTEST_MAIN_RUN_ALL_TESTS()

