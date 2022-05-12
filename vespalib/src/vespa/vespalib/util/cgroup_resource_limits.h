// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <map>

namespace vespalib {

/*
 * Class for getting cgroup resource limits (memory and cpu resources).
 *
 * Cgroups version 1 and version 2 are handled, but a mix of cgroups version 1
 * and cgroups version 2 is not handled.
 *
 * Systemd resource control unit settings applied to cgroups (cf.
 * systemd.resource-control(5) manual page on Linux) and
 * container resource limit (set by e.g. docker and podman) applied to cgroups
 * should be reflected in the sampled resource limits.
 *
 * The --cpus and --memory options for docker/podman create are reflected
 * in cgroup limits that are sampled, but the cgroup limit affected by the
 * --cpuset-cpus option is not yet sampled.
 *
 * For cgroups version 1, "memory.limit_in_bytes", "cpu.cfs_quota_us" and
 * "cpu.cfs_period_us" are sampled.
 *
 * For cgroups version 2, "memory.max" and "cpu.max" are sampled.
 *
 * This is intended to be used by vespa-proton-bin running on the host
 * or in a container, as a systemd service or not, with cgroups
 * version 1 or cgroups version 2.
 */
class CGroupResourceLimits {
    std::optional<uint64_t> _memory_limit;
    std::optional<uint32_t> _cpu_limit;
    std::string _base_path;      // "/sys/fs/cgroup"
    std::string _map_path;       // "/proc/self/cgroup"
    std::string _cgroup_v2_path; // Same cgroup path for all controllers
    std::map<std::string, std::string> _cgroup_v1_paths; // controller -> cgroup path

    void apply_memory_limit(uint64_t memory_limit);
    void apply_cpu_limit(uint32_t cpu_limit);
    void get_cgroup_paths();
    void foreach_cgroup_level(const std::string& controller, const std::string& cgroup_path, const std::function<void(const std::string&)>& callback);
    void foreach_cgroup_v1_level(const std::string& controller, const std::function<void(const std::string&)>& callback);
    void foreach_cgroup_v2_level(const std::function<void(const std::string&)>& callback);
    void get_memory_limits_v1(const std::string& dir);
    void get_memory_limits_v1();
    void get_cpu_limits_v1(const std::string& dir);
    void get_cpu_limits_v1();
    void get_limits_v1();
    void get_memory_limits_v2(const std::string& dir);
    void get_cpu_limits_v2(const std::string& dir);
    void get_limits_v2(const std::string& dir);
    void get_limits_v2();
public:
    CGroupResourceLimits();
    CGroupResourceLimits(const std::string& base_path, const std::string& map_path);
    ~CGroupResourceLimits();
    const std::optional<uint64_t>& get_memory_limit() const noexcept { return _memory_limit; }
    const std::optional<uint32_t>& get_cpu_limit() const noexcept { return _cpu_limit; }
};

}
