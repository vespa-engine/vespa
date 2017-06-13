// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/**
   This class handles ownership and creation of dynamic visitor libraries.
*/

#include "visitor.h"

namespace storage {

class VisitorLibraries {
public:
    typedef Visitor* (*VisitorFactoryFuncT)(StorageServerInterface& server,
                                            VisitorEnvironment& env,
                                            const vdslib::Parameters& params);

    struct LibMapEntry {
        std::shared_ptr<FastOS_DynamicLibrary> library;
        std::shared_ptr<VisitorEnvironment> environment;
        VisitorFactoryFuncT factory;
    };

    typedef std::map<std::string, LibMapEntry> LibMap;
    typedef std::pair<VisitorFactoryFuncT, VisitorEnvironment*> LibraryRef;

    static LibraryRef getLibrary(StorageServerInterface& storageServer, const std::string& libName, const std::string& libraryPath);

private:
    static LibMap _libs;
    static vespalib::Lock _libLock;

    static std::shared_ptr<VisitorEnvironment> getVisitorEnvironment(StorageServerInterface& storageServer,
                                          FastOS_DynamicLibrary& lib,
                                          const std::string& libName);
};

}
