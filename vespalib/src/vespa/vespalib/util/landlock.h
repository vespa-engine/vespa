// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#if defined(__linux__) && defined(__has_include)
#if __has_include("linux/landlock.h") // <linux/landlock.h> doesn't work due to token expansion, see:
                                      // https://gcc.gnu.org/bugzilla/show_bug.cgi?id=80005
#define VESPA_HAS_LANDLOCK 1
#else
#define VESPA_HAS_LANDLOCK 0
#endif // __has_include("linux/landlock.h")
#else
#define VESPA_HAS_LANDLOCK 0
#endif // defined(__linux__) && defined(__has_include)

#include <cstdint>
#include <filesystem>
#include <optional>
#include <vector>

namespace vespalib {

/**
 * Utilities for sandboxing a process using the Linux Landlock ABI.
 *
 * See: https://docs.kernel.org/userspace-api/landlock.html
 *
 * Landlocking is not retroactively applied to file descriptors opened _prior_ to its
 * invocation, so great care must be taken to ensure no monkey business can be done via
 * existing FDs.
 *
 * Only implemented on Linux with a sufficiently recent kernel; on other platforms this
 * is a no-op and provides no security at all!
 */
class Landlock {
public:
    static constexpr int READ = 1;
    static constexpr int WRITE = 2;
    static constexpr int EXEC = 4;

    struct AccessEntry {
        std::filesystem::path path;
        int                   allowed = 0;

        AccessEntry() = default;
        AccessEntry(std::filesystem::path path_, int allowed_);
        AccessEntry(const AccessEntry&);
        AccessEntry& operator=(const AccessEntry&);
        AccessEntry(AccessEntry&&) noexcept;
        AccessEntry& operator=(AccessEntry&&) noexcept;
        ~AccessEntry();
    };

    struct AccessRules {
        std::vector<AccessEntry> path_rules;
        // TODO net rules
        AccessRules() = default;
        AccessRules(const AccessRules&);
        AccessRules& operator=(const AccessRules&);
        AccessRules(AccessRules&&) noexcept;
        AccessRules& operator=(AccessRules&&) noexcept;
        ~AccessRules();
    };

    /*
     * Attempt to restrict the process' access to the system according to the provided
     * `access` rules.
     *
     * If std::nullopt is returned, the platform does not support Landlock.
     *
     * If false is returned, the platform _does_ support Landlock, but initialization
     * failed for some reason. The caller should strongly consider exiting with an error
     * code in this scenario.
     *
     * If true is returned, the process has been irreversibly sandboxed.
     */
    static std::optional<bool> landlock_self(const AccessRules& access);

    /*
     * Parses access rules from configured environment variables, if set.
     *
     * Variables used:
     *  - `VESPA_ENABLE_LANDLOCK`
     *  - `VESPA_LANDLOCK_PATHS`
     *
     * If `VESPA_ENABLE_LANDLOCK` is not set, or is `no|false|0`, Landlock will _not_ be used.
     *
     * `VESPA_LANDLOCK_PATHS` is a colon-separated set of paths (a-la the shell `PATH` variable),
     * stating which directories can be accessed post-landlock enforcement.
     *
     * By default, each path in `VESPA_LANDLOCK_PATHS` has read-only access, but this can be
     * overridden by appending `,<flags>` to each path component, where `<flags>` is one of
     * `ro`, `rw`, `rx`, `rwx`.
     *
     * Example: `VESPA_LANDLOCK_PATHS=/dev/:my_dir/,rw:my_bins/,rx`
     *
     * This allows read-only access for `/dev/`, read+write for `./my_dir/` and read+execute
     * for `./my_bins/`.
     *
     * If std::nullopt is returned, no explicit environment variable configuration for
     * landlocking was present.
     */
    static std::optional<AccessRules> from_env();
};

} // namespace vespalib
