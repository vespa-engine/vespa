// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "benchmark_spec.h"

#include <ostream>
#include <sstream>

namespace search::queryeval::test {

Spec term(FieldConfig field, double hit_ratio) {
    return Spec{TermSpec{std::move(field), hit_ratio}};
}

template <typename... Fs>
struct overloaded : Fs... { using Fs::operator()...; };

template <typename... Fs> overloaded(Fs...) -> overloaded<Fs...>;

std::ostream& operator<<(std::ostream& os, const Spec& s) {
    auto print_intermediate = [&](const char* name, const std::vector<Spec>& children) {
        os << name << "[";
        for (size_t i = 0; i < children.size(); ++i) {
            if (i) {
                os << ", ";
            }
            os << children[i];
        }
        os << "]";
    };

    std::visit(overloaded{
        [&](const TermSpec& t) {
            os << "Term(field=" << t.field.to_string() << ", r=" << t.hit_ratio << ")";
        },
        [&](const AndSpec& a) { print_intermediate("And", a.children); },
        [&](const OrSpec& o)  { print_intermediate("Or",  o.children); },
    }, s.node);
    return os;
}

std::string to_string(const Spec& s) {
    std::stringstream os;
    os << s;
    return os.str();
}

}
