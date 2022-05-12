// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cgroup_resource_limits.h"
#include "round_up_to_page_size.h"
#include <vespa/vespalib/io/fileutil.h>
#include <algorithm>
#include <cmath>
#include <fstream>
#include <limits>
#include <sstream>


namespace vespalib {

namespace {

std::string
combine_paths(const std::string& base_path, const std::string& controller, const std::string &cgroup_path)
{
    std::ostringstream os;
    os << base_path;
    if (!controller.empty()) {
        os << "/" << controller;
    }
    if (!cgroup_path.empty() && cgroup_path != "/") {
        if (cgroup_path[0] != '/') {
            os << "/";
        }
        os << cgroup_path;
    }
    return os.str();
}

const std::string empty_std_string;

}

CGroupResourceLimits::CGroupResourceLimits()
    : CGroupResourceLimits("/sys/fs/cgroup", "/proc/self/cgroup")
{
}

CGroupResourceLimits::CGroupResourceLimits(const std::string& base_path, const std::string& map_path)
    : _memory_limit(),
      _cpu_limit(),
      _base_path(base_path),
      _map_path(map_path),
      _cgroup_v2_path(),
      _cgroup_v1_paths()
{
    get_cgroup_paths();
    if (!_cgroup_v1_paths.empty()) {
        get_limits_v1();
    } else {
        get_limits_v2();
    }
}

CGroupResourceLimits::~CGroupResourceLimits() = default;

void
CGroupResourceLimits::apply_memory_limit(uint64_t memory_limit)
{
    if (!_memory_limit.has_value() || _memory_limit.value() > memory_limit) {
        _memory_limit = memory_limit;
    }
}

void
CGroupResourceLimits::apply_cpu_limit(uint32_t cpu_limit)
{
    if (!_cpu_limit.has_value() || _cpu_limit.value() > cpu_limit) {
        _cpu_limit = cpu_limit;
    }
}

void
CGroupResourceLimits::get_cgroup_paths()
{
    /*
     * See manual page for cgroups(7) on linux for format of /proc/[pid]/cgroup.
     */
    std::ifstream cg_self(_map_path);
    std::string line;
    while (std::getline(cg_self, line)) {
        if (line.size() >= 3 && line.substr(0, 3) == "0::") {
            _cgroup_v2_path = line.substr(3);
            continue;
        }
        auto col_pos = line.find(':');
        if (col_pos != std::string::npos) {
            auto name_pos = col_pos + 1;
            col_pos = line.find(':', name_pos);
            if (col_pos != std::string::npos) {
                auto cg_path = line.substr(col_pos + 1);
                auto comma_pos = line.find(',', name_pos);
                while (comma_pos < col_pos) {
                    auto subsystem = line.substr(name_pos, comma_pos - name_pos);
                    _cgroup_v1_paths[subsystem] = cg_path;
                    name_pos = comma_pos + 1;
                    comma_pos = line.find(',', name_pos);
                }
                auto subsystem = line.substr(name_pos, col_pos - name_pos);
                _cgroup_v1_paths[subsystem] = cg_path;
            }
        }
    }
}

void
CGroupResourceLimits::foreach_cgroup_level(const std::string& controller, const std::string& cgroup_path, const std::function<void(const std::string&)>& callback)
{
    auto dir = combine_paths(_base_path, controller, empty_std_string);
    if (!isDirectory(dir)) {
        return;
    }
    callback(dir);
    if (cgroup_path.empty() || cgroup_path == "/") {
        return;
    }
    auto slash_pos = cgroup_path.find('/', 1);
    while (slash_pos != std::string::npos) {
        dir = combine_paths(_base_path, controller, cgroup_path.substr(0, slash_pos));
        if (!isDirectory(dir)) {
            return;
        }
        callback(dir);
        slash_pos = cgroup_path.find('/', slash_pos + 1);
    }
    dir = combine_paths(_base_path, controller, cgroup_path);
    if (isDirectory(dir)) {
        callback(dir);
    }
}

void
CGroupResourceLimits::foreach_cgroup_v1_level(const std::string& controller, const std::function<void(const std::string&)>& callback)
{
    auto itr = _cgroup_v1_paths.find(controller);
    if (itr != _cgroup_v1_paths.end()) {
        foreach_cgroup_level(controller, itr->second, callback);
    } else {
        foreach_cgroup_level(controller, empty_std_string, callback);
    }
}

void
CGroupResourceLimits::foreach_cgroup_v2_level(const std::function<void(const std::string&)>& callback)
{
    foreach_cgroup_level(empty_std_string, _cgroup_v2_path, callback);
}

void
CGroupResourceLimits::get_memory_limits_v1(const std::string& dir)
{
    std::ifstream limitfile(dir + "/memory.limit_in_bytes");
    uint64_t memory_limit = std::numeric_limits<uint64_t>::max();
    limitfile >> memory_limit;
    if (limitfile.good() && (memory_limit < std::numeric_limits<int64_t>::max() - (round_up_to_page_size(1) - 1))) {
        apply_memory_limit(memory_limit);
    }
}

void
CGroupResourceLimits::get_memory_limits_v1()
{
    foreach_cgroup_v1_level("memory", [this](const std::string& dir) { get_memory_limits_v1(dir); });
}

void
CGroupResourceLimits::get_cpu_limits_v1(const std::string& dir)
{
    int32_t cpu_cfs_period_us = 0;
    int32_t cpu_cfs_quota_us = 0;
    std::ifstream period_file(dir + "/cpu.cfs_period_us");
    std::ifstream quota_file(dir + "/cpu.cfs_quota_us");
    period_file >> cpu_cfs_period_us;
    quota_file >> cpu_cfs_quota_us;
    if (quota_file.good() && period_file.good() && cpu_cfs_quota_us >= 0 && cpu_cfs_period_us > 0) {
        auto cpu = std::max(1.0, std::ceil(static_cast<double>(cpu_cfs_quota_us) / cpu_cfs_period_us));
        apply_cpu_limit(cpu);
    }
}

void
CGroupResourceLimits::get_cpu_limits_v1()
{
    foreach_cgroup_v1_level("cpu", [this](const std::string& dir) { get_cpu_limits_v1(dir); });
}

void
CGroupResourceLimits::get_limits_v1()
{
    get_memory_limits_v1();
    get_cpu_limits_v1();
}

void
CGroupResourceLimits::get_memory_limits_v2(const std::string& dir)
{
    std::ifstream limitfile(dir + "/memory.max");
    uint64_t memory_limit = std::numeric_limits<uint64_t>::max();
    limitfile >> memory_limit;
    if (limitfile.good()) {
        apply_memory_limit(memory_limit);
    }
}

void
CGroupResourceLimits::get_cpu_limits_v2(const std::string& dir)
{
    int32_t cpu_cfs_period_us = 0;
    int32_t cpu_cfs_quota_us = 0;
    std::ifstream cpu_max_file(dir + "/cpu.max");
    cpu_max_file >> cpu_cfs_quota_us >> cpu_cfs_period_us;
    if (cpu_max_file.good() && cpu_cfs_quota_us >= 0 && cpu_cfs_period_us > 0) {
        auto cpu = std::max(1.0, std::ceil(static_cast<double>(cpu_cfs_quota_us) / cpu_cfs_period_us));
        apply_cpu_limit(cpu);
    }
}

void
CGroupResourceLimits::get_limits_v2(const std::string& dir)
{
    get_memory_limits_v2(dir);
    get_cpu_limits_v2(dir);
}

void
CGroupResourceLimits::get_limits_v2()
{
    foreach_cgroup_v2_level([this](const std::string& dir) { get_limits_v2(dir); });
}

}
