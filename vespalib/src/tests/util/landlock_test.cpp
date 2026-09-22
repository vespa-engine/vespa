// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/landlock.h>
#include <vespa/vespalib/util/sanitizers.h>

#include <fcntl.h>
#include <gtest/gtest.h>

#include <cstdio>
#include <filesystem>

namespace fs = std::filesystem;

using namespace ::testing;

namespace vespalib {

struct LandlockDeathTest : Test {
protected:
    void SetUp() override {
        // We want to test that the Landlock API at least _compiles_ on all platforms
        // (i.e. we don't just ifdef out the entire test file), but we will casually
        // skip test execution on all non-Linux platforms.
#if VESPA_HAS_LANDLOCK
        fs::create_directory(fs::path("dir1"));
        fs::create_directory(fs::path("dir2"));
#else
        GTEST_SKIP() << "Landlock not supported on current platform";
#endif
    }

    void TearDown() override {
#if VESPA_HAS_LANDLOCK
        fs::remove_all(fs::path("dir1"));
        fs::remove_all(fs::path("dir2"));
#endif
    }
};

namespace {

// Invoking this function will irreversibly lock down the process permissions and must
// therefore be run in the context of a dedicated subprocess (EXPECT_EXIT et al).
void init_landlock(const char* landlock_path_env) {
    if (landlock_path_env) {
        setenv("VESPA_ENABLE_LANDLOCK", "1", 1);
        std::string path_str = landlock_path_env;
#ifdef VESPA_USE_SANITIZER
        // Sanitizers must have runtime read access to /proc/self and /tmp
        // or the process ends up stalling in a most unbecoming manner.
        if (!path_str.empty()) {
            path_str += ':';
        }
        path_str += "/proc/self:/tmp";
#endif
        setenv("VESPA_LANDLOCK_PATHS", path_str.c_str(), 1);
    } else {
        unsetenv("VESPA_ENABLE_LANDLOCK"); // Just in case...
    }
    auto from_env = Landlock::from_env();
    if (!from_env) {
        std::println(std::cerr, "no landlock env set");
        exit(1);
    }
    auto res = Landlock::landlock_self(*from_env);
    if (!res) {
        std::println(std::cerr, "landlock init failed");
        exit(2);
    }
}

void create_empty_file(const char* path) {
    int fd = open(path, O_CREAT | O_WRONLY | O_CLOEXEC, 0644);
    ASSERT_NE(fd, -1);
    ASSERT_EQ(close(fd), 0);
}

void subprocess_try_open_file(const char* landlock_path_env, const char* path, const int flags) {
    init_landlock(landlock_path_env);
    int fd = open(path, flags, 0644);
    if (fd == -1) {
        if (errno == EACCES) { // Expected error code when opening path not allowed by rule
            std::println(std::cerr, "open '{}' failed with EACCES", path);
            exit(3);
        } else {
            perror("open failed with non-EACCES error");
            exit(4);
        }
        exit(5);
    }
    close(fd);
    std::println(std::cerr, "opened OK");
    exit(0);
}

} // namespace

TEST_F(LandlockDeathTest, env_triggered_landlock_not_initialized_if_no_env_var_set) {
    EXPECT_EXIT(subprocess_try_open_file(nullptr, "dir1/foo", O_RDONLY), ExitedWithCode(1), "no landlock env set");
}

TEST_F(LandlockDeathTest, landlock_enabled_without_rules_disallows_all) {
    EXPECT_EXIT(subprocess_try_open_file("", "dir1/foo", O_CREAT | O_TRUNC | O_RDWR), ExitedWithCode(3),
                "open 'dir1/foo' failed with EACCES");
}

TEST_F(LandlockDeathTest, can_allow_writes_to_directory) {
    EXPECT_EXIT(subprocess_try_open_file("dir1/,rw", "dir1/foo", O_CREAT | O_TRUNC | O_RDWR), ExitedWithCode(0),
                "opened OK");
}

TEST_F(LandlockDeathTest, can_specify_multiple_directories) {
    EXPECT_EXIT(subprocess_try_open_file("dir1/,ro,ro:dir2/,rw", "dir2/foo", O_CREAT | O_TRUNC | O_RDWR),
                ExitedWithCode(0), "opened OK");
    EXPECT_EXIT(subprocess_try_open_file("dir1/,rw,ro:dir2/,ro", "dir2/foo", O_CREAT | O_TRUNC | O_RDWR),
                ExitedWithCode(3), "open 'dir2/foo' failed with EACCES");
}

TEST_F(LandlockDeathTest, paths_are_by_default_read_only_access_denied_case) {
    ASSERT_NO_FATAL_FAILURE(create_empty_file("dir1/foo"));
    EXPECT_EXIT(subprocess_try_open_file("dir1/", "dir1/foo", O_CREAT | O_TRUNC | O_RDWR), ExitedWithCode(3),
                "open 'dir1/foo' failed with EACCES");
}

TEST_F(LandlockDeathTest, paths_are_by_default_read_only_access_granted_case) {
    ASSERT_NO_FATAL_FAILURE(create_empty_file("dir1/bar"));
    EXPECT_EXIT(subprocess_try_open_file("dir1/", "dir1/bar", O_RDONLY), ExitedWithCode(0), "opened OK");
}

#if !VESPA_HAS_LANDLOCK

TEST(LandlockUnsupportedTest, nullopt_is_returned) {
    EXPECT_FALSE(Landlock::from_env().has_value());
    Landlock::AccessRules rules;
    EXPECT_FALSE(Landlock::landlock_self(rules).has_value());
}

#endif // !VESPA_HAS_LANDLOCK

// TODO net access lockdown configuration

} // namespace vespalib
