// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/portal/portal.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

using namespace vbench;
using vespalib::Process;

using InputReader = vespalib::InputReader;
using OutputWriter = vespalib::OutputWriter;
using Portal = vespalib::Portal;

auto null_crypto = std::make_shared<vespalib::NullCryptoEngine>();
auto tls_opts = vespalib::test::make_tls_options_for_testing();
auto tls_crypto = std::make_shared<vespalib::TlsCryptoEngine>(tls_opts);

void write_file(const vespalib::string &file_name, const vespalib::string &content) {
    int fd = creat(file_name.c_str(), 0600);
    ASSERT_TRUE(fd >= 0);
    ssize_t res = write(fd, content.data(), content.size());
    ASSERT_EQUAL(res, ssize_t(content.size()));
    int res2 = close(fd);
    ASSERT_EQUAL(res2, 0);
}

TEST("vbench usage") {
    vespalib::string out;
    EXPECT_FALSE(Process::run("../../apps/vbench/vbench_app", out));
    fprintf(stderr, "%s\n", out.c_str());
}

struct MyGet : vespalib::Portal::GetHandler {
    std::atomic<size_t> cnt;
    void get(vespalib::Portal::GetRequest request) override {
        ++cnt;
        request.respond_with_content("text/plain", "data");
    };
};

struct Servers {
    MyGet my_get;
    MyGet my_tls_get;
    Portal::SP portal;
    Portal::SP tls_portal;
    Portal::Token::UP root;
    Portal::Token::UP tls_root;
    Servers() : my_get(), my_tls_get(),
                portal(Portal::create(null_crypto, 0)),
                tls_portal(Portal::create(tls_crypto, 0)),
                root(portal->bind("/", my_get)),
                tls_root(tls_portal->bind("/", my_tls_get))
    {
        write_file("ca_certs.pem", tls_opts.ca_certs_pem());
        write_file("certs.pem", tls_opts.cert_chain_pem());
        write_file("test.key", tls_opts.private_key_pem());
    }
    ~Servers() {
        write_file("test.key", "garbage\n");
    }
};

TEST_MT_F("run vbench", 2, Servers()) {
    if (thread_id == 0) {
        vespalib::string out;
        EXPECT_TRUE(Process::run(strfmt("sed 's/_LOCAL_PORT_/%d/' vbench.cfg.template > vbench.cfg", f1.portal->listen_port()).c_str()));
        EXPECT_TRUE(Process::run("../../apps/vbench/vbench_app run vbench.cfg 2> vbench.out", out));
        fprintf(stderr, "null crypto: %s\n", out.c_str());
        EXPECT_GREATER(f1.my_get.cnt, 10u);
    } else {
        vespalib::string tls_out;
        EXPECT_TRUE(Process::run(strfmt("sed 's/_LOCAL_PORT_/%d/' vbench.tls.cfg.template > vbench.tls.cfg", f1.tls_portal->listen_port()).c_str()));
        EXPECT_TRUE(Process::run("../../apps/vbench/vbench_app run vbench.tls.cfg 2> vbench.tls.out", tls_out));
        fprintf(stderr, "tls crypto: %s\n", tls_out.c_str());
        EXPECT_GREATER(f1.my_tls_get.cnt, 10u);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
