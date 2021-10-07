// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/source.h>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/noncopyable.hpp>

namespace config {

class FileSpec;
class DirSpec;

class FileSource : public Source,
                   public vespalib::noncopyable
{
private:
    IConfigHolder::SP _holder;
    const vespalib::string _fileName;
    int64_t _lastLoaded;
    int64_t _generation;

    std::vector<vespalib::string> readConfigFile(const vespalib::string & fileName);
    int64_t getLast(const vespalib::string & fileName);

public:
    FileSource(const IConfigHolder::SP & holder, const vespalib::string & fileName);
    void getConfig() override;
    void close() override;
    void reload(int64_t generation) override;
};

} // namespace config

