// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "visitorlibraries.h"
#include <vespa/defaults.h>

#include <vespa/log/log.h>

LOG_SETUP(".visiting.libraryloader");

namespace storage {

VisitorLibraries::LibMap VisitorLibraries::_libs;
vespalib::Lock VisitorLibraries::_libLock;

/**
 * Utility function to get a dynamic library.
 * Assumes _libLock has been grabbed before calling.
 */
VisitorLibraries::LibraryRef
VisitorLibraries::getLibrary(StorageServerInterface& storageServer, const std::string& libName, const std::string& libraryPath)
{
    vespalib::LockGuard guard(_libLock);

    LibMap::iterator it = _libs.find(libName);
    if (it != _libs.end()) {
        return LibraryRef(it->second.factory, it->second.environment.get());
    }

    std::shared_ptr<FastOS_DynamicLibrary> lib(new FastOS_DynamicLibrary);
    std::string file = libraryPath + "lib" + libName + ".so";
    if (!lib->Open(file.c_str())) {
        std::string error = lib->GetLastErrorString();
        std::string absfile = vespa::Defaults::vespaHome();
        absfile.append("libexec/vespa/storage/lib" + libName + ".so");
        if (!lib->Open(absfile.c_str())) {
            LOG(error, "Could not load library %s: %s",
                       file.c_str(), error.c_str());
            return LibraryRef();
        }
    }
    std::shared_ptr<VisitorEnvironment> env(
            getVisitorEnvironment(storageServer, *lib, libName));

    LibMapEntry entry;
    entry.library = lib;
    entry.environment = env;
    entry.factory = lib.get() ? (VisitorFactoryFuncT) lib->GetSymbol("makeVisitor") : 0;
    _libs[libName] = entry;

    return LibraryRef(entry.factory, env.get());
}

std::shared_ptr<VisitorEnvironment>
VisitorLibraries::getVisitorEnvironment(StorageServerInterface& storageServer, FastOS_DynamicLibrary& lib,
                                     const std::string& libName)
{
    typedef VisitorEnvironment::UP
            (*VisitorEnvFuncT)(StorageServerInterface& server);
    VisitorEnvFuncT factoryFunc
        = (VisitorEnvFuncT) lib.GetSymbol("makeVisitorEnvironment");
    if (factoryFunc == 0) {
        std::string err = lib.GetLastErrorString();
        LOG(error, "Unable to load symbol 'makeVisitorEnvironment' from "
                   "'%s': %s", libName.c_str(), err.c_str());
        return std::shared_ptr<VisitorEnvironment>();
    }
    return std::shared_ptr<VisitorEnvironment>(
            factoryFunc(storageServer).release());
}

}
