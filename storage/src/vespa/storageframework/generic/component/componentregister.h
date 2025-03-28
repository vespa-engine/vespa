// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::ComponentRegister
 * \ingroup component
 *
 * \brief Application server implements this to get overview of all components.
 *
 * By implementing this class, the application server will get all the
 * components it needs to manage using an interface containing just what it
 * needs to minimize dependencies.
 */
#pragma once

#include <string>

namespace storage::framework {

struct ManagedComponent;

struct ComponentRegister {
    virtual ~ComponentRegister() = default;

    virtual void registerComponent(ManagedComponent&) = 0;
    virtual void requestShutdown(std::string_view reason) = 0;
};

}
