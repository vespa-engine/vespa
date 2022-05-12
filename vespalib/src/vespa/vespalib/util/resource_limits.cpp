// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_limits.h"
#include "cgroup_resource_limits.h"
#include <unistd.h>
#include <thread>

namespace vespalib {

ResourceLimits::ResourceLimits(uint64_t memory, uint32_t cpu)
    : _memory(memory),
      _cpu(cpu)
{
}

ResourceLimits
ResourceLimits::create()
{
    uint64_t memory = sysconf(_SC_PHYS_PAGES) * sysconf(_SC_PAGESIZE);
    uint32_t cpu = std::thread::hardware_concurrency();
    CGroupResourceLimits cgroup_limits;
    auto& cg_memory = cgroup_limits.get_memory_limit();
    auto& cg_cpu = cgroup_limits.get_cpu_limit();
    if (cg_memory.has_value() && cg_memory.value() < memory) {
        memory = cg_memory.value();
    }
    if (cg_cpu.has_value() && cg_cpu.value() < cpu) {
        cpu = cg_cpu.value();
    }
    return ResourceLimits(memory, cpu);
}

}
