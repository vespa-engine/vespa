// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <cstdint>

namespace vespalib {

/*
 * Class describing some hardware on the machine.
 */
class HwInfo
{
public:
    class Disk {
    private:
        uint64_t _sizeBytes;
        bool     _slow;
        bool     _shared;
    public:
        Disk(uint64_t sizeBytes_, bool slow_, bool shared_) noexcept
            : _sizeBytes(sizeBytes_), _slow(slow_), _shared(shared_) {}
        uint64_t sizeBytes() const noexcept { return _sizeBytes; }
        [[nodiscard]] bool slow() const noexcept { return _slow; }
        [[nodiscard]] bool shared() const noexcept { return _shared; }
        bool operator == (const Disk & rhs) const noexcept {
            return (_sizeBytes == rhs._sizeBytes) && (_slow == rhs._slow) && (_shared == rhs._shared);
        }
    };

    class Memory {
    private:
        uint64_t _sizeBytes;
    public:
        Memory(uint64_t sizeBytes_) noexcept : _sizeBytes(sizeBytes_) {}
        uint64_t sizeBytes() const noexcept { return _sizeBytes; }
        bool operator == (const Memory & rhs) const noexcept { return _sizeBytes == rhs._sizeBytes; }
    };

    class Cpu {
    private:
        uint32_t _cores;
    public:
        Cpu(uint32_t cores_) noexcept : _cores(std::max(1u, cores_)) { }
        uint32_t cores() const noexcept { return _cores; }
        bool operator == (const Cpu & rhs) const noexcept { return _cores == rhs._cores; }
    };

private:
    Disk   _disk;
    Memory _memory;
    Cpu    _cpu;

public:
    HwInfo() noexcept
        : _disk(0, false, false),
          _memory(0),
          _cpu(1)
    {
    }

    HwInfo(const Disk &disk_,
           const Memory &memory_,
           const Cpu &cpu_) noexcept
        : _disk(disk_),
          _memory(memory_),
          _cpu(cpu_)
    {
    }

    const Disk &disk() const noexcept { return _disk; }
    const Memory &memory() const noexcept { return _memory; }
    const Cpu &cpu() const noexcept { return _cpu; }
    bool operator == (const HwInfo & rhs) const noexcept {
        return (_cpu == rhs._cpu) && (_disk == rhs._disk) && (_memory == rhs._memory);
    }
};

}
