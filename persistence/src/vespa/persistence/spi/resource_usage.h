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
    
    ResourceUsage(double disk_usage, double memory_usage)
        : _disk_usage(disk_usage),
          _memory_usage(memory_usage)
    {
    }

    ResourceUsage()
        : ResourceUsage(0.0, 0.0)
    {
    }

    double get_disk_usage() const noexcept { return _disk_usage; }
    double get_memory_usage() const noexcept { return _memory_usage; }
};

}

