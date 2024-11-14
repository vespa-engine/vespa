// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/fileheadercontext.h>
#include <cstdlib>

namespace vespalib { class GenericHeader; }

namespace search::index {

class DummyFileHeaderContext : public common::FileHeaderContext {
    bool _disableFileName;
    std::string _hostName;
    pid_t _pid;

    static std::string _creator;
public:
    DummyFileHeaderContext();
    ~DummyFileHeaderContext();
    void disableFileName();
    void addTags(vespalib::GenericHeader &header, const std::string &name) const override;
    static void setCreator(const std::string &creator);
};

}
