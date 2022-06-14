// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/proton_disk_layout.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <filesystem>

using search::index::DummyFileHeaderContext;
using search::transactionlog::client::TransLogClient;
using search::transactionlog::TransLogServer;
using proton::DocTypeName;
using proton::ProtonDiskLayout;
using proton::Transport;

static constexpr unsigned int tlsPort = 9018;

static const vespalib::string baseDir("testdb");
static const vespalib::string documentsDir(baseDir + "/documents");

struct FixtureBase
{
    FixtureBase() { std::filesystem::remove_all(std::filesystem::path(baseDir)); }
    ~FixtureBase() { std::filesystem::remove_all(std::filesystem::path(baseDir)); }
};

struct DiskLayoutFixture {
    DummyFileHeaderContext  _fileHeaderContext;
    Transport               _transport;
    TransLogServer          _tls;
    vespalib::string        _tlsSpec;
    ProtonDiskLayout        _diskLayout;

    DiskLayoutFixture();
    ~DiskLayoutFixture();

    void createDirs(const std::set<vespalib::string> &dirs) {
        for (const auto &dir : dirs) {
            std::filesystem::create_directory(std::filesystem::path(documentsDir + "/" + dir));
        }
    }
    void createDomains(const std::set<vespalib::string> &domains) {
        TransLogClient tlc(_transport.transport(), _tlsSpec);
        for (const auto &domain : domains) {
            ASSERT_TRUE(tlc.create(domain));
        }
    }

    std::set<vespalib::string> listDomains() {
        std::vector<vespalib::string> domainVector;
        TransLogClient tlc(_transport.transport(), _tlsSpec);
        ASSERT_TRUE(tlc.listDomains(domainVector));
        std::set<vespalib::string> domains;
        for (const auto &domain : domainVector) {
            domains.emplace(domain);
        }
        return domains;
    }

    std::set<vespalib::string> listDirs() {
        std::set<vespalib::string> dirs;
        auto names = vespalib::listDirectory(documentsDir);
        for (const auto &name : names) {
            if (vespalib::isDirectory(documentsDir + "/" + name)) {
                dirs.emplace(name);
            }
        }
        return dirs;
    }

    void initAndPruneUnused(const std::set<vespalib::string> names)
    {
        std::set<DocTypeName> docTypeNames;
        for (const auto &name: names) {
            docTypeNames.emplace(name);
        }
        _diskLayout.initAndPruneUnused(docTypeNames);
    }

    void assertDirs(const std::set<vespalib::string> &expDirs) {
        EXPECT_EQUAL(expDirs, listDirs());
    }

    void assertDomains(const std::set<vespalib::string> &expDomains)
    {
        EXPECT_EQUAL(expDomains, listDomains());
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
};

TEST_F("require that empty config is ok", Fixture) {
    TEST_DO(f.assertDirs({}));
    TEST_DO(f.assertDomains({}));
}

TEST_F("require that disk layout is preserved", FixtureBase)
{
    {
        DiskLayoutFixture diskLayout;
        diskLayout.createDirs({"foo", "bar"});
        diskLayout.createDomains({"bar", "baz"});
    }
    {
        DiskLayoutFixture diskLayout;
        TEST_DO(diskLayout.assertDirs({"foo", "bar"}));
        TEST_DO(diskLayout.assertDomains({"bar", "baz"}));
    }
}

TEST_F("require that used dir is preserved", Fixture)
{
    f.createDirs({"foo"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    TEST_DO(f.assertDirs({"foo"}));
    TEST_DO(f.assertDomains({"foo"}));
}

TEST_F("require that unused dir is removed", Fixture)
{
    f.createDirs({"foo"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"bar"});
    TEST_DO(f.assertDirs({}));
    TEST_DO(f.assertDomains({}));
}

TEST_F("require that interrupted remove is completed", Fixture)
{
    f.createDirs({"foo.removed"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    TEST_DO(f.assertDirs({}));
    TEST_DO(f.assertDomains({}));
}

TEST_F("require that early interrupted remove is completed", Fixture)
{
    f.createDirs({"foo", "foo.removed"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    TEST_DO(f.assertDirs({}));
    TEST_DO(f.assertDomains({}));
}

TEST_F("require that live document db dir remove works", Fixture)
{
    f.createDirs({"foo"});
    f.createDomains({"foo"});
    f.initAndPruneUnused({"foo"});
    TEST_DO(f.assertDirs({"foo"}));
    TEST_DO(f.assertDomains({"foo"}));
    f._diskLayout.remove(DocTypeName("foo"));
    TEST_DO(f.assertDirs({}));
    TEST_DO(f.assertDomains({}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
