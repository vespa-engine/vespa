// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/child_process.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/eval/eval/test/test_io.h>

using namespace vespalib;
using namespace vespalib::eval::test;
using vespalib::make_string_short::fmt;
using vespalib::slime::JsonFormat;
using vespalib::slime::Inspector;

vespalib::string module_build_path("../../../../");
vespalib::string binary = module_build_path + "src/apps/analyze_onnx_model/vespa-analyze-onnx-model";
vespalib::string probe_cmd = binary + " --probe-types";

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
std::string source_dir = get_source_dir();
std::string guess_batch_model = source_dir + "/../../tensor/onnx_wrapper/guess_batch.onnx";

//-----------------------------------------------------------------------------

void read_until_eof(Input &input) {
    for (auto mem = input.obtain(); mem.size > 0; mem = input.obtain()) {
        input.evict(mem.size);
    }
}

// Output adapter used to write to stdin of a child process
class ChildIn : public Output {
    ChildProcess &_child;
    SimpleBuffer _output;
public:
    ChildIn(ChildProcess &child) : _child(child) {}
    WritableMemory reserve(size_t bytes) override {
        return _output.reserve(bytes);
    }
    Output &commit(size_t bytes) override {
        _output.commit(bytes);
        Memory buf = _output.obtain();
        ASSERT_TRUE(_child.write(buf.data, buf.size));
        _output.evict(buf.size);
        return *this;
    }
};

// Input adapter used to read from stdout of a child process
class ChildOut : public Input {
    ChildProcess &_child;
    SimpleBuffer _input;
public:
    ChildOut(ChildProcess &child)
      : _child(child)
    {
        EXPECT_TRUE(_child.running());
        EXPECT_TRUE(!_child.failed());
    }
    Memory obtain() override {
        if ((_input.get().size == 0) && !_child.eof()) {
            WritableMemory buf = _input.reserve(4_Ki);
            uint32_t res = _child.read(buf.data, buf.size);
            ASSERT_TRUE((res > 0) || _child.eof());
            _input.commit(res);
        }
        return _input.obtain();
    }
    Input &evict(size_t bytes) override {
        _input.evict(bytes);
        return *this;
    }
};

//-----------------------------------------------------------------------------

void dump_message(const char *prefix, const Slime &slime) {
    SimpleBuffer buf;
    slime::JsonFormat::encode(slime, buf, true);
    auto str = buf.get().make_string();
    fprintf(stderr, "%s%s\n", prefix, str.c_str());
}

class Server {
private:
    TimeBomb _bomb;
    ChildProcess _child;
    ChildIn _child_stdin;
    ChildOut _child_stdout;
public:
    Server(vespalib::string cmd)
      : _bomb(60),
        _child(cmd.c_str()),
        _child_stdin(_child),
        _child_stdout(_child) {}
    ~Server();
    Slime invoke(const Slime &req) {
        dump_message("request --> ", req);
        write_compact(req, _child_stdin);
        Slime reply;
        ASSERT_TRUE(JsonFormat::decode(_child_stdout, reply));
        dump_message("  reply <-- ", reply);
        return reply;
    }
};
Server::~Server() {
    _child.close();
    read_until_eof(_child_stdout);
    ASSERT_TRUE(_child.wait());
    ASSERT_TRUE(!_child.running());
    ASSERT_TRUE(!_child.failed());
}

//-----------------------------------------------------------------------------

TEST_F("require that output types can be probed", Server(probe_cmd)) {
    Slime params;
    params.setObject();
    params.get().setString("model", guess_batch_model);
    params.get().setObject("inputs");
    params["inputs"].setString("in1", "tensor<float>(x[3])");
    params["inputs"].setString("in2", "tensor<float>(x[3])");
    Slime result = f1.invoke(params);
    EXPECT_EQUAL(result["outputs"]["out"].asString().make_string(), vespalib::string("tensor<float>(d0[3])"));
}

//-----------------------------------------------------------------------------

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
