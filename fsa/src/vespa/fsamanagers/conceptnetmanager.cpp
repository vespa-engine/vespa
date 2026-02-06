// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    conceptnetmanager.cpp
 * @brief   Concept network manager class implementation.
 *
 */

#include "conceptnetmanager.h"

namespace fsa {

// {{{ ConceptNetManager::~ConceptNetManager()

ConceptNetManager::~ConceptNetManager() {
    for (LibraryIterator it = _library.begin(); it != _library.end(); ++it) {
        delete it->second;
    }
}

// }}}

// {{{ ConceptNetManager::load()

bool ConceptNetManager::load(const std::string& id, const std::string& fsafile, const std::string& datafile) {
    ConceptNet::Handle* newcn =
        new ConceptNet::Handle(fsafile.c_str(), datafile.length() > 0 ? datafile.c_str() : nullptr);

    if (newcn == nullptr || !(*newcn)->isOk()) {
        delete newcn;
        return false;
    }

    std::lock_guard guard(_lock);
    {
        LibraryIterator it = _library.find(id);
        if (it != _library.end()) {
            delete it->second;
            it->second = newcn;
        } else
            _library.insert(Library::value_type(id, newcn));
    }

    return true;
}

// }}}
// {{{ ConceptNetManager::get()

ConceptNet::Handle* ConceptNetManager::get(const std::string& id) const {
    ConceptNet::Handle* newhandle = nullptr;
    std::shared_lock    guard(_lock);
    {
        LibraryConstIterator it = _library.find(id);
        if (it != _library.end()) {
            newhandle = new ConceptNet::Handle(*(it->second));
        }
    }
    return newhandle;
}

// }}}
// {{{ ConceptNetManager::drop()

void ConceptNetManager::drop(const std::string& id) {
    std::lock_guard guard(_lock);
    {
        LibraryIterator it = _library.find(id);
        if (it != _library.end()) {
            delete it->second;
            _library.erase(it);
        }
    }
}

// }}}
// {{{ ConceptNetManager::clear()

void ConceptNetManager::clear() {
    std::lock_guard guard(_lock);
    {
        for (LibraryIterator it = _library.begin(); it != _library.end(); ++it)
            delete it->second;
        _library.clear();
    }
}

// }}}

} // namespace fsa
