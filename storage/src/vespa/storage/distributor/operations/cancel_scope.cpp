// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "cancel_scope.h"

namespace storage::distributor {

CancelScope::CancelScope()
    : _cancelled_nodes(),
      _fully_cancelled(false)
{
}

CancelScope::CancelScope(fully_cancelled_ctor_tag) noexcept
    : _cancelled_nodes(),
      _fully_cancelled(true)
{
}

CancelScope::CancelScope(CancelledNodeSet nodes) noexcept
    : _cancelled_nodes(std::move(nodes)),
      _fully_cancelled(false)
{
}

CancelScope::~CancelScope() = default;

CancelScope::CancelScope(const CancelScope&) = default;
CancelScope& CancelScope::operator=(const CancelScope&) = default;

CancelScope::CancelScope(CancelScope&&) noexcept = default;
CancelScope& CancelScope::operator=(CancelScope&&) noexcept = default;

void CancelScope::add_cancelled_node(uint16_t node) {
    _cancelled_nodes.insert(node);
}

void CancelScope::merge(const CancelScope& other) {
    _fully_cancelled |= other._fully_cancelled;
    // Not using iterator insert(first, last) since that explicitly resizes,
    for (uint16_t node : other._cancelled_nodes) {
        _cancelled_nodes.insert(node);
    }
}

CancelScope CancelScope::of_fully_cancelled() noexcept {
    return CancelScope(fully_cancelled_ctor_tag{});
}

CancelScope CancelScope::of_node_subset(CancelledNodeSet nodes) noexcept {
    return CancelScope(std::move(nodes));
}

}
