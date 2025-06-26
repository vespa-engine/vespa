// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>
#include <string>

namespace vespalib {
    class GenericHeader;
}

namespace search::common {

class FileHeaderContext
{
public:
    FileHeaderContext();
    virtual ~FileHeaderContext();

    virtual void addTags(vespalib::GenericHeader &header, const std::string &name) const = 0;

    static void addCreateAndFreezeTime(vespalib::GenericHeader &header);
    static void setFreezeTime(vespalib::GenericHeader &header);
    static std::chrono::steady_clock::duration get_flush_duration(const vespalib::GenericHeader& header);
    static std::chrono::steady_clock::duration make_flush_duration(const std::chrono::steady_clock::time_point& create_time);
};

}
