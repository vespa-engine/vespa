// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test_data.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <cassert>
#include <cstring>
#include <fstream>

namespace vespalib::test {

bool
TestDataBase::equiv_buffers(const nbostream& lhs, const nbostream& rhs)
{
    return lhs.size() == rhs.size() && memcmp(lhs.data(), rhs.data(), lhs.size()) == 0;
}

nbostream
TestDataBase::read_buffer_from_file(const std::string& path)
{
    auto file = std::ifstream(path, std::ios::in | std::ios::binary | std::ios::ate);
    auto size = file.tellg();
    file.seekg(0);
    vespalib::alloc::Alloc buf = vespalib::alloc::Alloc::alloc(size);
    file.read(static_cast<char *>(buf.get()), size);
    assert(file.good());
    file.close();
    return nbostream(std::move(buf), size);
}

void
TestDataBase::write_buffer_to_file(const nbostream& buf, const std::string& path)
{
    write_buffer_to_file(std::string_view{buf.data(), buf.size()}, path);
}

void
TestDataBase::write_buffer_to_file(std::string_view buf, const std::string& path)
{
    auto file = std::ofstream(path, std::ios::out | std::ios::binary | std::ios::trunc);
    file.write(buf.data(), buf.size());
    assert(file.good());
    file.close();
}

}
