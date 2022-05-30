// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slime_explorer.h"

namespace vespalib {

namespace {

struct SelfState : slime::ObjectTraverser {
    Slime result;
    SelfState() : result() { result.setObject(); }
    void field(const Memory &key, const slime::Inspector &value) override {
        if (value.type().getId() != slime::OBJECT::ID) {
            slime::inject(value, slime::ObjectInserter(result.get(), key));
        }
    }
};

struct ChildrenNames : slime::ObjectTraverser {
    std::vector<vespalib::string> result;
    void field(const Memory &key, const slime::Inspector &value) override {
        if (value.type().getId() == slime::OBJECT::ID) {
            result.push_back(key.make_string());
        }
    }
};

} // namespace vespalib::<unnamed>

void
SlimeExplorer::get_state(const slime::Inserter &inserter, bool full) const
{
    SelfState state;
    _self.traverse(state);
    if (state.result.get().fields() > 0) {
        if (full) {
            state.result.get().setBool("full", true);
        }
        slime::inject(state.result.get(), inserter);
    }
}

std::vector<vespalib::string>
SlimeExplorer::get_children_names() const
{
    ChildrenNames names;
    _self.traverse(names);
    return names.result;
}

std::unique_ptr<StateExplorer>
SlimeExplorer::get_child(vespalib::stringref name) const
{
    slime::Inspector &child = _self[name];
    if (!child.valid()) {
        return std::unique_ptr<StateExplorer>(nullptr);
    }
    return std::unique_ptr<StateExplorer>(new SlimeExplorer(child));
}

} // namespace vespalib
