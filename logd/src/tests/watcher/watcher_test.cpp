// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <logd/config_subscriber.h>
#include <vespa/config/common/configcontext.h>
#include <logd/watcher.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <filesystem>
#include <fstream>
#include <regex>
#include <thread>
#include <mutex>
#include <condition_variable>

using cloud::config::log::LogdConfigBuilder;
using cloud::config::log::LogdConfig;
using config::ConfigSet;
using config::ConfigUri;
using config::IConfigContext;
using config::ConfigContext;
using vespalib::ThreadStackExecutor;
using vespalib::makeLambdaTask;
using namespace std::chrono_literals;

std::regex rotated_log(R"(vespa.log-[0-9]*-[0-9]*-[0-9]*\.[0-9]*-[0-9]*-[0-9]*)");

namespace logdemon {

namespace {

struct ConfigFixture {
    const std::string configId;
    LogdConfigBuilder logdBuilder;
    ConfigSet set;
    std::shared_ptr<IConfigContext> context;
    int idcounter;

    ConfigFixture(const std::string & id);
    ~ConfigFixture();
    void reload() { context->reload(); }
};

ConfigFixture::ConfigFixture(const std::string & id)
    : configId(id),
      logdBuilder(),
      set(),
      context(std::make_shared<ConfigContext>(set)),
      idcounter(-1)
{
    logdBuilder.logserver.use = false;
    logdBuilder.rotate.size = 1024;
    set.addBuilder(configId, &logdBuilder);
}

ConfigFixture::~ConfigFixture() = default;

struct DummyForwarder : public Forwarder {
    std::mutex lock;
    std::condition_variable cond;
    std::vector<std::string> lines;
    DummyForwarder();
    ~DummyForwarder() override;
    void forwardLine(std::string_view log_line) override {
        std::lock_guard guard(lock);
        lines.emplace_back(log_line);
        cond.notify_all();
    }
    void flush() override { }
    int badLines() const override { return 0; }
    void resetBadLines() override { }
    std::vector<std::string> getLines() {
        std::lock_guard guard(lock);
        return lines;
    }
    void waitLineCount(size_t lineCount) {
        std::unique_lock guard(lock);
        cond.wait_for(guard, 10s, [this, lineCount]() { return lineCount <= lines.size(); });
    }
};

DummyForwarder::DummyForwarder()
    : Forwarder(),
      lock(),
      cond(),
      lines()
{ }
DummyForwarder::~DummyForwarder() = default;

struct WatcherFixture
{
    DummyForwarder fwd;
    ConfigSubscriber subscriber;
    Watcher watcher;

    WatcherFixture(ConfigFixture &cfg);
    ~WatcherFixture();
};

WatcherFixture::WatcherFixture(ConfigFixture &cfg)
    : fwd(),
      subscriber(config::ConfigUri(cfg.configId, cfg.context)),
      watcher(subscriber, fwd)
{
    subscriber.latch();
}

WatcherFixture::~WatcherFixture() = default;

}

class WatcherTest : public ::testing::Test {
protected:
    std::unique_ptr<ConfigFixture> _cfg;
    std::unique_ptr<WatcherFixture> _watcher;
    ThreadStackExecutor _executor;

    void setup_watcher();
    void run_watcher();
    void stop_watcher();
    void log_line(const std::string &line);
    void assert_lines(const std::vector<std::string> &lines);
    void remove_files();
    void remove_rotated();
    int count_rotated();
public:
    WatcherTest();
    ~WatcherTest();
};

WatcherTest::WatcherTest()
    : _executor(1)
{
    remove_files();
    setenv("VESPA_LOG_TARGET", "file:vespa.log", true);
    std::filesystem::create_directories(std::filesystem::path("var/db/vespa")); // for logd.donestate
    _cfg = std::make_unique<ConfigFixture>("testconfigid");
}

WatcherTest::~WatcherTest()
{
    remove_files();
}

void
WatcherTest::setup_watcher()
{
    _watcher = std::make_unique<WatcherFixture>(*_cfg);
}

void
WatcherTest::run_watcher()
{
    // Spin off watcher task
    _executor.execute(makeLambdaTask([this]() { _watcher->watcher.watchfile(); }));
}

void
WatcherTest::stop_watcher()
{
    _cfg->reload();
    _executor.sync();
}

void
WatcherTest::log_line(const std::string &line)
{
    std::ofstream log_file("vespa.log", std::ios::out | std::ios::app);
    log_file << line << std::endl;
}

void
WatcherTest::assert_lines(const std::vector<std::string> &lines)
{
    EXPECT_EQ(lines, _watcher->fwd.getLines());
}

void
WatcherTest::remove_files()
{
    std::filesystem::remove_all(std::filesystem::path("var"));
    remove_rotated();
    std::filesystem::remove(std::filesystem::path("vespa.log"));
}

void
WatcherTest::remove_rotated()
{
    auto dirlist = vespalib::listDirectory(".");
    for (const auto &entry : dirlist) {
        if (std::regex_match(entry.data(), entry.data() + entry.size(), rotated_log)) {
            std::filesystem::remove(std::filesystem::path(entry));
        }
    }
}
    
int
WatcherTest::count_rotated()
{
    int result = 0;
    auto dirlist = vespalib::listDirectory(".");
    for (const auto &entry : dirlist) {
        if (std::regex_match(entry.data(), entry.data() + entry.size(), rotated_log)) {
            ++result;
        }
    }
    return result;
}

TEST_F(WatcherTest, require_that_watching_no_logging_works)
{
    setup_watcher();
    run_watcher();
    stop_watcher();
    assert_lines({});
    EXPECT_EQ(0, count_rotated());
}

TEST_F(WatcherTest, require_that_watching_simple_logging_works)
{
    setup_watcher();
    run_watcher();
    log_line("foo");
    _watcher->fwd.waitLineCount(1);
    stop_watcher();
    EXPECT_EQ(0, count_rotated());
    assert_lines({"foo"});
}

TEST_F(WatcherTest, require_that_watching_can_resume)
{
    setup_watcher();
    run_watcher();
    log_line("foo");
    _watcher->fwd.waitLineCount(1);
    stop_watcher();
    assert_lines({"foo"});
    setup_watcher();
    run_watcher();
    log_line("bar");
    log_line("baz");
    _watcher->fwd.waitLineCount(2);
    stop_watcher();
    assert_lines({"bar", "baz"});
    // remove state file. Old entry will resurface
    std::filesystem::remove(std::filesystem::path("var/db/vespa/logd.donestate"));
    setup_watcher();
    run_watcher();
    _watcher->fwd.waitLineCount(3);
    stop_watcher();
    assert_lines({"foo", "bar", "baz"});
}

TEST_F(WatcherTest, require_that_watching_can_rotate_log_files)
{
    setup_watcher();
    run_watcher();
    std::vector<std::string> exp_lines;
    for (int i = 0; i < 100; ++i) {
        std::ostringstream os;
        os << "this is a malformatted " << std::setw(3) << i <<
            std::setw(0) << " line but who cares ?";
        log_line(os.str());
        exp_lines.push_back(os.str());
        std::this_thread::sleep_for(100ms);
        if (count_rotated() > 0) {
            break;
        }
    }
    _watcher->fwd.waitLineCount(exp_lines.size());
    stop_watcher();
    assert_lines(exp_lines);
    EXPECT_LT(0, count_rotated());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
