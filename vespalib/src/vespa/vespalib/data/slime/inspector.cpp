// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inspector.h"
#include "object_traverser.h"
#include "array_traverser.h"
#include <cassert>

namespace vespalib::slime {

using Path = std::vector<std::variant<size_t,std::string_view>>;
using Hook = std::function<bool(const Path &, const Inspector &, const Inspector &)>;

namespace {

struct EqualState {
    Path path;
    Hook hook;
    bool failed;
    explicit EqualState(Hook hook_in)
      : path(), hook(std::move(hook_in)), failed(false) {}
    void mismatch(const Inspector &a, const Inspector &b) {
        if (!failed && !hook(path, a, b)) {
            failed = true;
        }
    }
    void check_equal(const Inspector &a, const Inspector &b);
};

struct EqualObject : ObjectTraverser {
    EqualState &state;
    const Inspector &rhs;
    EqualObject(EqualState &state_in, const Inspector &rhs_in) noexcept
      : state(state_in), rhs(rhs_in) {}
    void field(const Memory &symbol, const Inspector &inspector) final {
        if (!state.failed) {
            state.path.emplace_back(symbol.make_stringview());
            state.check_equal(inspector, rhs[symbol]);
            state.path.pop_back();
        }
    }
};

struct MissingFields : ObjectTraverser {
    EqualState &state;
    const Inspector &lhs;
    MissingFields(EqualState &state_in, const Inspector &lhs_in) noexcept
      : state(state_in), lhs(lhs_in) {}
    void field(const Memory &symbol, const Inspector &inspector) final {
        if (!state.failed) {
            const Inspector &field = lhs[symbol];
            if (!field.valid()) {
                state.path.emplace_back(symbol.make_stringview());
                state.mismatch(field, inspector);
                state.path.pop_back();
            }
        }
    }
};

void
EqualState::check_equal(const Inspector &a, const Inspector &b) {
    bool equal(a.type().getId() == b.type().getId());
    if (equal) {
        switch (a.type().getId()) {
        case NIX::ID:
            equal = a.valid() == b.valid();
            break;
        case BOOL::ID:
            equal = a.asBool() == b.asBool();
            break;
        case LONG::ID:
            equal = a.asLong() == b.asLong();
            break;
        case DOUBLE::ID:
            equal = a.asDouble() == b.asDouble();
            break;
        case STRING::ID:
            equal = a.asString() == b.asString();
            break;
        case DATA::ID:
            equal = a.asData() == b.asData();
            break;
        case ARRAY::ID:
            {
                size_t cnt = std::max(a.entries(), b.entries());
                for (size_t i = 0; !failed && i < cnt; ++i) {
                    path.emplace_back(i);
                    check_equal(a[i], b[i]);
                    path.pop_back();
                }
            }
            break;
        case OBJECT::ID:
            {
                EqualObject cmp(*this, b);
                MissingFields missing(*this, a);
                a.traverse(cmp);
                b.traverse(missing);
            }
            break;
        default:
            abort();
            break;
        }
    }
    if (!equal) {
        mismatch(a, b);
    }
}

} // <unnamed>

bool are_equal(const Inspector &a, const Inspector &b, Hook allow_mismatch) {
    EqualState state(std::move(allow_mismatch));
    state.check_equal(a, b);
    return !state.failed;
}

bool operator==(const Inspector &a, const Inspector &b) {
    return are_equal(a, b, [](const Path &, const Inspector &, const Inspector &)noexcept{ return false; });
}

std::ostream &operator<<(std::ostream &os, const Inspector &inspector) {
    os << inspector.toString();
    return os;
}

} // namespace vespalib::slime
