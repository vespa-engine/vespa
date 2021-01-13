// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace storage::spi {

/*
 * Class representing resource usage for persistence provider.
 * Numbers are normalized to be between 0.0 and 1.0
 */
class ResourceUsage
{
    double _disk_usage;
    double _memory_usage;
public:
    
    ResourceUsage()
        : _disk_usage(0.0),
          _memory_usage(0.0)
    {
    }

    void set_disk_usage(double disk_usage) noexcept { _disk_usage = disk_usage; }
    void set_memory_usage(double memory_usage) noexcept { _memory_usage = memory_usage; }
    double get_disk_usage() const noexcept { return _disk_usage; }
    double get_memory_usage() const noexcept { return _memory_usage; }
};

}

