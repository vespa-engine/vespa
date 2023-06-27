// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "currentindex.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <utility>

namespace search::expression {

class CurrentIndexSetup {
public:
    class Usage {
    private:
        friend class CurrentIndexSetup;
        vespalib::hash_set<vespalib::string> _unbound;
        void notify_unbound_struct_usage(vespalib::stringref name);
    public:
        Usage();
        ~Usage();
        [[nodiscard]] bool has_single_unbound_struct() const noexcept {
            return (_unbound.size() == 1);
        }
        vespalib::stringref get_unbound_struct_name() const;
        class Bind {
        private:
            CurrentIndexSetup &_setup;
        public:
            Bind(CurrentIndexSetup &setup, Usage &usage) noexcept;
            ~Bind();
        };
    };
private:
    vespalib::hash_map<vespalib::string, const CurrentIndex *> _bound;
    Usage *_usage;
    [[nodiscard]] Usage *capture(Usage *usage) noexcept {
        return std::exchange(_usage, usage);
    }
public:
    CurrentIndexSetup();
    ~CurrentIndexSetup();
    [[nodiscard]] const CurrentIndex *resolve(vespalib::stringref field_name) const;    
    void bind(vespalib::stringref struct_name, const CurrentIndex &index);
};

}
