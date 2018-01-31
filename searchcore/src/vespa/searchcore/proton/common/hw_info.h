// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace proton {

/*
 * Class describing some hardware on the machine.
 */
class HwInfo
{
public:
    class Disk {
    private:
        uint64_t _sizeBytes;
        bool _slow;
        bool _shared;
    public:
        Disk(uint64_t sizeBytes_, bool slow_, bool shared_)
            : _sizeBytes(sizeBytes_), _slow(slow_), _shared(shared_) {}
        uint64_t sizeBytes() const { return _sizeBytes; }
        bool slow() const { return _slow; }
        bool shared() const { return _shared; }
        bool operator == (const Disk & rhs) const {
            return (_sizeBytes == rhs._sizeBytes) && (_slow == rhs._slow) && (_shared == rhs._shared);
        }
    };

    class Memory {
    private:
        uint64_t _sizeBytes;
    public:
        Memory(uint64_t sizeBytes_) : _sizeBytes(sizeBytes_) {}
        uint64_t sizeBytes() const { return _sizeBytes; }
        bool operator == (const Memory & rhs) const { return _sizeBytes == rhs._sizeBytes; }
    };

    class Cpu {
    private:
        uint32_t _cores;
    public:
        Cpu(uint32_t cores_) : _cores(cores_) {}
        uint32_t cores() const { return _cores; }
        bool operator == (const Cpu & rhs) const { return _cores == rhs._cores; }
    };

private:
    Disk _disk;
    Memory _memory;
    Cpu _cpu;

public:
    HwInfo()
        : _disk(0, false, false),
          _memory(0),
          _cpu(0)
    {
    }

    HwInfo(const Disk &disk_,
           const Memory &memory_,
           const Cpu &cpu_)
        : _disk(disk_),
          _memory(memory_),
          _cpu(cpu_)
    {
    }

    const Disk &disk() const { return _disk; }
    const Memory &memory() const { return _memory; }
    const Cpu &cpu() const { return _cpu; }
    bool operator == (const HwInfo & rhs) const {
        return (_cpu == rhs._cpu) && (_disk == rhs._disk) && (_memory == rhs._memory);
    }
};

}
