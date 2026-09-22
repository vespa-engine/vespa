// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "landlock.h"

namespace vespalib {

Landlock::AccessEntry::AccessEntry(std::filesystem::path path_, int allowed_)
    : path(std::move(path_)), allowed(allowed_) {
}
Landlock::AccessEntry::AccessEntry(const AccessEntry&) = default;
Landlock::AccessEntry& Landlock::AccessEntry::operator=(const AccessEntry&) = default;
Landlock::AccessEntry::AccessEntry(AccessEntry&&) noexcept = default;
Landlock::AccessEntry& Landlock::AccessEntry::operator=(AccessEntry&&) noexcept = default;
Landlock::AccessEntry::~AccessEntry() = default;

Landlock::AccessRules::AccessRules(const AccessRules&) = default;
Landlock::AccessRules& Landlock::AccessRules::operator=(const AccessRules&) = default;
Landlock::AccessRules::AccessRules(AccessRules&&) noexcept = default;
Landlock::AccessRules& Landlock::AccessRules::operator=(AccessRules&&) noexcept = default;
Landlock::AccessRules::~AccessRules() = default;

} // namespace vespalib

#if VESPA_HAS_LANDLOCK

#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/util/string_escape.h>

#include <fcntl.h>
#include <linux/landlock.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <cstdio>
#include <format>
#include <iostream>
#include <ranges>
#include <string>
#include <string_view>

namespace fs = std::filesystem;

namespace vespalib {
namespace {

// Many kernel versions don't expose syscall wrappers for Landlock stuff, so we have
// to use the raw syscall interface. Prefix with "my_" to avoid collisions for those
// kernels where they _are_ defined.

[[nodiscard]] int my_landlock_create_ruleset(const landlock_ruleset_attr* attr, const size_t size,
                                             const uint32_t flags) noexcept {
    return static_cast<int>(syscall(__NR_landlock_create_ruleset, attr, size, flags));
}

[[nodiscard]] int my_landlock_add_rule(const int ruleset_fd, const landlock_rule_type rule_type,
                                       const void* rule_attr, const uint32_t flags) noexcept {
    return static_cast<int>(syscall(__NR_landlock_add_rule, ruleset_fd, rule_type, rule_attr, flags));
}

[[nodiscard]] int my_landlock_restrict_self(const int ruleset_fd, const uint32_t flags) noexcept {
    return static_cast<int>(syscall(__NR_landlock_restrict_self, ruleset_fd, flags));
}

struct FdWrapper {
    int fd = -1;
    FdWrapper(int fd_) noexcept : fd(fd_) {}
    ~FdWrapper() {
        if (fd != -1) {
            close(fd);
        }
    }
};

void add_path_ruleset(const int ruleset_fd, const uint64_t supported_flags, const std::filesystem::path& path,
                      const int wanted_access) {
    const FdWrapper parent(open(path.c_str(), O_PATH | O_CLOEXEC));
    if (parent.fd == -1) {
        std::println(std::cerr, "landlock: failed to open \"{}\": {}", escape(path.c_str()), getLastErrorString());
        return;
    }
    landlock_path_beneath_attr path_beneath{};
    path_beneath.parent_fd = parent.fd;
    struct ::stat statbuf{};
    if (fstat(path_beneath.parent_fd, &statbuf) == -1) {
        std::println(std::cerr, "landlock: failed to stat \"{}\": {}", escape(path.c_str()), getLastErrorString());
        return;
    }
    uint64_t access_flags = 0;
    if ((wanted_access & Landlock::READ) != 0) {
        access_flags |= LANDLOCK_ACCESS_FS_READ_FILE | LANDLOCK_ACCESS_FS_READ_DIR;
    }
    if ((wanted_access & Landlock::WRITE) != 0) {
        access_flags |= LANDLOCK_ACCESS_FS_READ_FILE | LANDLOCK_ACCESS_FS_READ_DIR | LANDLOCK_ACCESS_FS_WRITE_FILE |
                        LANDLOCK_ACCESS_FS_REMOVE_FILE | LANDLOCK_ACCESS_FS_TRUNCATE | LANDLOCK_ACCESS_FS_MAKE_REG;
    }
    if ((wanted_access & Landlock::EXEC) != 0) {
        access_flags |= LANDLOCK_ACCESS_FS_EXECUTE;
    }
    path_beneath.allowed_access = access_flags & supported_flags;
    int err = my_landlock_add_rule(ruleset_fd, LANDLOCK_RULE_PATH_BENEATH, &path_beneath, 0);
    if (err) {
        std::println(std::cerr, "landlock: failed to add rule for \"{}\": {}", escape(path.c_str()),
                     getLastErrorString());
    }
}

} // namespace

std::optional<bool> Landlock::landlock_self(const AccessRules& access) {
    // Note: we only deal with flags up to and including Landlock ABI version 6.
    // This will be extended once we're on a newer kernel.
    // We specify more flags than we currently grant via write privileges.
    // The expectation is that a Vespa process will not create UNIX sockets,
    // fifos, block devices etc. during normal operation. We'll get there if
    // we get there (in which case we'll probably add some dedicated access
    // config flags for them).
    uint64_t fs_flags = LANDLOCK_ACCESS_FS_EXECUTE | LANDLOCK_ACCESS_FS_READ_FILE | LANDLOCK_ACCESS_FS_READ_DIR |
                        LANDLOCK_ACCESS_FS_WRITE_FILE | LANDLOCK_ACCESS_FS_REMOVE_DIR |
                        LANDLOCK_ACCESS_FS_REMOVE_FILE | LANDLOCK_ACCESS_FS_MAKE_CHAR | LANDLOCK_ACCESS_FS_MAKE_DIR |
                        LANDLOCK_ACCESS_FS_MAKE_REG | LANDLOCK_ACCESS_FS_MAKE_SOCK | LANDLOCK_ACCESS_FS_MAKE_FIFO |
                        LANDLOCK_ACCESS_FS_MAKE_BLOCK | LANDLOCK_ACCESS_FS_MAKE_SYM | LANDLOCK_ACCESS_FS_REFER |
                        LANDLOCK_ACCESS_FS_TRUNCATE | LANDLOCK_ACCESS_FS_IOCTL_DEV;
    // No UDP support in our current ABI (present in >= v10)
    // Don't restrict the network until we have figured out a good way to configure it
    uint64_t net_flags = 0; // TODO LANDLOCK_ACCESS_NET_BIND_TCP | LANDLOCK_ACCESS_NET_CONNECT_TCP;
    uint64_t scoped_flags = LANDLOCK_SCOPE_ABSTRACT_UNIX_SOCKET | LANDLOCK_SCOPE_SIGNAL;
    // Based on https://docs.kernel.org/userspace-api/landlock.html and
    // https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/tree/samples/landlock/sandboxer.c
    const int abi = my_landlock_create_ruleset(nullptr, 0, LANDLOCK_CREATE_RULESET_VERSION);
    if (abi < 0) {
        return std::nullopt; // No landlock support (or disabled in kernel)
    }
    // Landlock requires NO_NEW_PRIVS to be in effect, as otherwise it'd be possible to get
    // around the sandboxing by abusing a setuid binary (and possibly other fun tricks).
    // See https://www.kernel.org/doc/Documentation/prctl/no_new_privs.txt
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
        perror("prctl(PR_SET_NO_NEW_PRIVS)");
        return false;
    }
    // Adjust to lowest common denominator between the Linux headers we're compiled
    // against vs. what's actually supported on the underlying running kernel.
    switch (abi) {
    case 1:
        fs_flags &= ~LANDLOCK_ACCESS_FS_REFER;
        [[fallthrough]];
    case 2:
        fs_flags &= ~LANDLOCK_ACCESS_FS_TRUNCATE;
        [[fallthrough]];
    case 3:
        // This is technically a no-op since we don't restrict the network right
        // now, but this will be needed once we do, so keep it in.
        net_flags &= ~(LANDLOCK_ACCESS_NET_BIND_TCP | LANDLOCK_ACCESS_NET_CONNECT_TCP);
        [[fallthrough]];
    case 4:
        fs_flags &= ~LANDLOCK_ACCESS_FS_IOCTL_DEV;
        [[fallthrough]];
#if defined(LANDLOCK_SCOPE_ABSTRACT_UNIX_SOCKET) && defined(LANDLOCK_SCOPE_SIGNAL)
    case 5:
        scoped_flags &= ~(LANDLOCK_SCOPE_ABSTRACT_UNIX_SOCKET | LANDLOCK_SCOPE_SIGNAL);
        [[fallthrough]];
#endif
    default:;
    }

    landlock_ruleset_attr ruleset_attr{};
    ruleset_attr.handled_access_fs = fs_flags;
    ruleset_attr.handled_access_net = net_flags;
    ruleset_attr.scoped = scoped_flags;
    const FdWrapper ruleset(my_landlock_create_ruleset(&ruleset_attr, sizeof(ruleset_attr), 0));
    if (ruleset.fd < 0) {
        perror("landlock_create_ruleset");
        return false;
    }
    for (const auto& entry : access.path_rules) {
        add_path_ruleset(ruleset.fd, fs_flags, entry.path, entry.allowed);
    }
    if (my_landlock_restrict_self(ruleset.fd, 0) != 0) {
        perror("landlock_restrict_self");
        return false;
    }
    return true;
}

namespace {
[[nodiscard]] bool is_env_false(std::string_view str) noexcept {
    return str == "0" || str == "no" || str == "false";
}
} // namespace

std::optional<Landlock::AccessRules> Landlock::from_env() {
    const char* enabled_env = getenv("VESPA_ENABLE_LANDLOCK");
    // If the env var is set, treat every string that isn't a known "no" as a "yes".
    // This is safer than the inverse where someone sets the var to "True" and it fails
    // to match due to uncased checking, thus ending up not sandboxing the process.
    if (!enabled_env || is_env_false(enabled_env)) {
        return std::nullopt;
    }
    AccessRules access;
    const char* paths_env = getenv("VESPA_LANDLOCK_PATHS");
    if (!paths_env) {
        return access;
    }
    std::string paths_str(paths_env);
    using std::operator""sv;
    for (const auto entry : std::views::split(paths_str, ":"sv)) {
        std::string_view entry_sv(entry);
        std::string_view dir_sv = entry_sv;
        int              flags = 0;
        auto             comma_pos = entry_sv.find_first_of(',');
        if (comma_pos != std::string_view::npos) {
            dir_sv = entry_sv.substr(0, comma_pos);
            auto access_sv = entry_sv.substr(comma_pos + 1);
            // Treat access types as enums rather than just checking for the presence
            // of 'r', 'w' or 'x' to minimize the chance of accidental (or deliberate)
            // argument confusion.
            if (access_sv == "ro") {
                flags = READ;
            } else if (access_sv == "rw") {
                flags = READ | WRITE;
            } else if (access_sv == "rx") {
                flags = READ | EXEC;
            } else if (access_sv == "rwx") {
                flags = READ | WRITE | EXEC;
            } else {
                std::println(std::cerr, "Unknown access specifier: \"{}\". Supported are: ro, rw, rx, rwx",
                             escape(access_sv));
            }
            // The presence of multiple commas indicates a misconfiguration or perhaps
            // even some sneaky stuff going on.
            if (access_sv.contains(',')) {
                std::println(std::cerr, "landlock: illegal duplicate comma in path entry \"{}\"", escape(entry_sv));
                flags = 0;
            }
        } else {
            flags = READ; // no comma == default read-only access
        }
        if (flags != 0) {
            access.path_rules.emplace_back(fs::path(dir_sv), flags);
        } // else: no rule entry means no access by default
    }
    return access;
}

} // namespace vespalib

#else // VESPA_HAS_LANDLOCK

// No-op implementation

namespace vespalib {

std::optional<bool> Landlock::landlock_self(const AccessRules&) {
    return std::nullopt;
}

std::optional<Landlock::AccessRules> Landlock::from_env() {
    return std::nullopt;
}

} // namespace vespalib

#endif // VESPA_HAS_LANDLOCK
