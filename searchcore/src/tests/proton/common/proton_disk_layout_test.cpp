// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/proton_disk_layout.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/test/port_numbers.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <filesystem>
#include <vespa/vespalib/gtest/gtest.h>

using search::index::DummyFileHeaderContext;
using search::transactionlog::client::TransLogClient;
using search::transactionlog::TransLogServer;
using proton::DocTypeName;
using proton::ProtonDiskLayout;
using proton::Transport;

namespace {
constexpr unsigned int tlsPort = proton::test::port_numbers::proton_disk_layout_tls_port;

const std::string baseDir("testdb");
const std::string documentsDir(baseDir + "/documents");

struct FixtureBase
{
    FixtureBase() { std::filesystem::remove_all(std::filesystem::path(baseDir)); }
    ~FixtureBase() { std::filesystem::remove_all(std::filesystem::path(baseDir)); }
};

struct DiskLayoutFixture {
    DummyFileHeaderContext  _fileHeaderContext;
    Transport               _transport;
    TransLogServer          _tls;
    std::string        _tlsSpec;
    ProtonDiskLayout        _diskLayout;

    DiskLayoutFixture();
    ~DiskLayoutFixture();

    void createDirs(const std::set<std::string> &dirs) {
        for (const auto &dir : dirs) {
            std::filesystem::create_directory(std::filesystem::path(documentsDir + "/" + dir));
        }
    }
    void createDomains(const std::set<std::string> &domains) {
        TransLogClient tlc(_transport.transport(), _tlsSpec);
        for (const auto &domain : domains) {
            ASSERT_TRUE(tlc.create(domain));
        }
    }

    std::set<std::string> listDomains() {
        std::vector<std::string> domainVector;
        TransLogClient tlc(_transport.transport(), _tlsSpec);
        EXPECT_TRUE(tlc.listDomains(domainVector));
        std::set<std::string> domains;
        for (const auto &domain : domainVector) {
            domains.emplace(domain);
        }
        return domains;
    }

    std::set<std::string> listDirs() {
        std::set<std::string> dirs;
        auto names = vespalib::listDirectory(documentsDir);
        for (const auto &name : names) {
            if (std::filesystem::is_directory(std::filesystem::path(documentsDir + "/" + name))) {
                dirs.emplace(name);
            }
        }
        return dirs;
    }

    void initAndPruneUnused(const std::set<std::string> names)
    {
        std::set<DocTypeName> docTypeNames;
        for (const auto &name: names) {
            docTypeNames.emplace(name);
        }
        _diskLayout.initAndPruneUnused(docTypeNames);
    }
};

DiskLayoutFixture::DiskLayoutFixture()
    : _fileHeaderContext(),
      _transport(),
      _tls(_transport.transport(), "tls", tlsPort, baseDir, _fileHeaderContext),
      _tlsSpec(vespalib::make_string("tcp/localhost:%u", tlsPort)),
      _diskLayout(_transport.transport(), baseDir, _tlsSpec)
{
}

DiskLayoutFixture::~DiskLayoutFixture() = default;

struct Fixture : public FixtureBase, public DiskLayoutFixture
{
    Fixture()
        : FixtureBase(),
          DiskLayoutFixture()
    {
    }
    ~Fixture();
};

Fixture::~Fixture() = default;

}

TEST(ProtonDiskLayoutTest, require_that_empty_config_is_ok)
{
    Fixture f;
    EXPECT_EQ((std::set<std::string>{}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{}), f.listDomains());
}

TEST(ProtonDiskLayoutTest, require_that_disk_layout_is_preserved)
{
    FixtureBase f;
    {
        DiskLayoutFixture diskLayout;
        diskLayout.createDirs({"foo", "bar"});
        diskLayout.createDomains({"bar", "baz"});
    }
    {
        DiskLayoutFixture diskLayout;
        EXPECT_EQ((std::set<std::string>{"foo", "bar"}), diskLayout.listDirs());
        EXPECT_EQ((std::set<std::string>{"bar", "baz"}), diskLayout.listDomains());
    }
}

TEST(ProtonDiskLayoutTest, require_that_used_dir_is_preserved)
{
    Fixture f;
    f.createDirs({"foo"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    EXPECT_EQ((std::set<std::string>{"foo"}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{"foo"}), f.listDomains());
}

TEST(ProtonDiskLayoutTest, require_that_unused_dir_is_removed)
{
    Fixture f;
    f.createDirs({"foo"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"bar"});
    EXPECT_EQ((std::set<std::string>{}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{}), f.listDomains());
}

TEST(ProtonDiskLayoutTest, require_that_interrupted_remove_is_completed)
{
    Fixture f;
    f.createDirs({"foo.removed"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    EXPECT_EQ((std::set<std::string>{}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{}), f.listDomains());
}

TEST(ProtonDiskLayoutTest, require_that_early_interrupted_remove_is_completed)
{
    Fixture f;
    f.createDirs({"foo", "foo.removed"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    EXPECT_EQ((std::set<std::string>{}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{}), f.listDomains());
}

TEST(ProtonDiskLayoutTest, require_that_live_document_db_dir_remove_works)
{
    Fixture f;
    f.createDirs({"foo"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    EXPECT_EQ((std::set<std::string>{"foo"}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{"foo"}), f.listDomains());
    f._diskLayout.remove(DocTypeName("foo"));
    EXPECT_EQ((std::set<std::string>{}), f.listDirs());
    EXPECT_EQ((std::set<std::string>{}), f.listDomains());
}
