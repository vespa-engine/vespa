// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>

namespace vespalib { class Lock; }
namespace documentapi {

class NodeState;

/**
 * This class is a factory to create a tree of {@link NodeState} objects from a parseable node state
 * string. The naming of this class is intended to capture the fact that this annotated service tree actually
 * contains the state of each service in the system.
 */
class SystemState {
private:
    std::unique_ptr<NodeState>      _root;
    std::unique_ptr<vespalib::Lock> _lock;

    friend class SystemStateHandle;

    /**
     * Constructs a new system state object to encapsulate a given root node state. This method is private; the only way
     * to create a new instance is through the {@link #create} method.
     *
     * @param root The root node state.
     */
    SystemState(std::unique_ptr<NodeState> root);

public:
    ~SystemState();
    SystemState(const SystemState &) = delete;
    SystemState & operator = (const SystemState &) = delete;
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<SystemState> UP;

    /**
     * Creates a system state expression from a system state string.
     *
     * @param state The string to parse as a system state.
     * @return The created node state tree.
     * @throws RuntimeException Thrown if the string could not be parsed.
     */
    static SystemState::UP newInstance(const string &state);
};

}
