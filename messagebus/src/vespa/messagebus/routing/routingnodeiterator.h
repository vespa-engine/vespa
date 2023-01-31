// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "route.h"

namespace mbus {

class RoutingNode;
class Reply;

/**
 * Implements an iterator for the child routing contexts of this. Use {@link
 * RoutingContext#getChildIterator()} to retrieve an instance of this.
 */
class RoutingNodeIterator {
private:
    std::vector<RoutingNode*>::iterator _pos, _end;

public:
    /**
     * Constructs a new iterator based on a given list.
     *
     * @param children The list to iterate through.
     */
    explicit RoutingNodeIterator(std::vector<RoutingNode*> &children);

    /**
     * Returns whether or not this iterator is valid.
     *
     * @return True if we are still pointing to a valid entry.
     */
    [[nodiscard]] bool isValid() const { return _pos != _end; }

    /**
     * Steps to the next child in the map.
     *
     * @return This, to allow chaining.
     */
    RoutingNodeIterator &next();

    /**
     * Skips the given number of children.
     *
     * @param num The number of children to skip.
     * @return This, to allow chaining.
     */
    RoutingNodeIterator &skip(uint32_t num);

    /**
     * Returns the route of the current child.
     *
     * @return The route.
     */
    [[nodiscard]] const Route &getRoute() const;

    /**
     * Removes and returns the reply of the current child. This is the correct way of reusing a reply of a
     * child node, the {@link #getReplyRef()} should be used when just inspecting a child reply.
     *
     * @return The reply.
     */
    std::unique_ptr<Reply> removeReply();

    /**
     * Returns the reply of the current child.
     *
     * @return The reply.
     */
    [[nodiscard]] const Reply &getReplyRef() const;
};

} // mbus

