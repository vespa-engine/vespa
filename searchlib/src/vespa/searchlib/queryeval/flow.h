// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/vespalib/util/small_vector.h>
#include <cstddef>
#include <algorithm>
#include <functional>

// Model how boolean result decisions flow through intermediate nodes
// of different types based on relative estimates for sub-expressions

namespace search::queryeval {

// Encapsulate information about strictness and in-flow in a structure
// for convenient parameter passing. We do not need an explicit value
// in the strict case since strict basically means the receiving end
// will eventually decide the actual flow. We use a rate of 1.0 for
// strict flow to indicate that the corpus is not reduced externally.
class InFlow {
private:
    double _value;
public:
    constexpr InFlow(bool strict, double rate) noexcept
      : _value(strict ? -1.0 : std::max(rate, 0.0)) {}
    constexpr InFlow(bool strict) noexcept : InFlow(strict, 1.0) {}
    constexpr InFlow(double rate) noexcept : InFlow(false, rate) {}
    constexpr bool strict() noexcept { return _value < 0.0; }
    constexpr double rate() noexcept { return strict() ? 1.0 : _value; }
};

struct FlowStats {
    double estimate;
    double cost;
    double strict_cost;
    constexpr FlowStats(double estimate_in, double cost_in, double strict_cost_in) noexcept
      : estimate(estimate_in), cost(cost_in), strict_cost(strict_cost_in) {}
    auto operator <=>(const FlowStats &rhs) const noexcept = default;
};

namespace flow {

// the default adapter expects the shape of std::unique_ptr<Blueprint>
// with respect to estimate, cost and strict_cost.
struct DefaultAdapter {
    double estimate(const auto &child) const noexcept { return child->estimate(); }
    double cost(const auto &child) const noexcept { return child->cost(); }
    double strict_cost(const auto &child) const noexcept { return child->strict_cost(); }
};

template <typename T>
concept DefaultAdaptable = requires(const T &t) {
    { t->estimate() } -> std::same_as<double>;
    { t->cost() } -> std::same_as<double>;
    { t->strict_cost() } -> std::same_as<double>;
};

// adapter making it possible to use FlowStats directly for testing
struct DirectAdapter {
    double estimate(const auto &child) const noexcept { return child.estimate; }
    double cost(const auto &child) const noexcept { return child.cost; }
    double strict_cost(const auto &child) const noexcept { return child.strict_cost; }
};

template <typename T>
concept DirectAdaptable = requires(const T &t) {
    { t.estimate } -> std::same_as<const double &>;
    { t.cost } -> std::same_as<const double &>;
    { t.strict_cost } -> std::same_as<const double &>;
};

auto make_adapter(const auto &children) {
    using type = std::remove_cvref_t<decltype(children)>::value_type;
    static_assert(DefaultAdaptable<type> || DirectAdaptable<type>, "unable to resolve children adapter");
    if constexpr (DefaultAdaptable<type>) {
        return DefaultAdapter();
    } else {
        return DirectAdapter();
    }
}

template <typename ADAPTER, typename T>
struct IndirectAdapter {
    const T &data;
    [[no_unique_address]] ADAPTER adapter;
    IndirectAdapter(ADAPTER adapter_in, const T &data_in) noexcept
      : data(data_in), adapter(adapter_in) {}
    double estimate(size_t child) const noexcept { return adapter.estimate(data[child]); }
    double cost(size_t child) const noexcept { return adapter.cost(data[child]); }
    double strict_cost(size_t child) const noexcept { return adapter.strict_cost(data[child]); }
};

inline auto make_index(size_t size) {
    vespalib::SmallVector<uint32_t> index(size);
    for (size_t i = 0; i < size; ++i) {
        index[i] = i;
    }
    return index;
}

template <typename ADAPTER>
struct MinAndCost {
    // sort children to minimize total cost of AND flow
    [[no_unique_address]] ADAPTER adapter;
    MinAndCost(ADAPTER adapter_in) noexcept : adapter(adapter_in) {}
    bool operator () (const auto &a, const auto &b) const noexcept {
        return (1.0 - adapter.estimate(a)) * adapter.cost(b) > (1.0 - adapter.estimate(b)) * adapter.cost(a);
    }
};

template <typename ADAPTER>
struct MinOrCost {
    // sort children to minimize total cost of OR flow
    [[no_unique_address]] ADAPTER adapter;
    MinOrCost(ADAPTER adapter_in) noexcept : adapter(adapter_in) {}
    bool operator () (const auto &a, const auto &b) const noexcept {
        return adapter.estimate(a) * adapter.cost(b) > adapter.estimate(b) * adapter.cost(a);
    }
};

template <typename ADAPTER, typename T, typename F>
double estimate_of(ADAPTER adapter, const T &children, F flow) {
    for (const auto &child: children) {
        flow.add(adapter.estimate(child));
    }
    return flow.estimate();
}

template <template <typename> typename ORDER, typename ADAPTER, typename T>
void sort(ADAPTER adapter, T &children) {
    std::sort(children.begin(), children.end(), ORDER(adapter));
}

template <template <typename> typename ORDER, typename ADAPTER, typename T>
void sort_partial(ADAPTER adapter, T &children, size_t offset) {
    if (children.size() > offset) {
        std::sort(children.begin() + offset, children.end(), ORDER(adapter));
    }
}

template <typename ADAPTER, typename T, typename F>
double ordered_cost_of(ADAPTER adapter, const T &children, F flow) {
    double total_cost = 0.0;
    for (const auto &child: children) {
        double child_cost = flow.strict() ? adapter.strict_cost(child) : (flow.flow() * adapter.cost(child));
        flow.update_cost(total_cost, child_cost);
        flow.add(adapter.estimate(child));
    }
    return total_cost;
}

template <typename ADAPTER, typename T>
size_t select_strict_and_child(ADAPTER adapter, const T &children) {
    size_t idx = 0;
    double cost = 0.0;
    size_t best_idx = 0;
    double best_diff = 0.0;
    double est = 1.0;
    for (const auto &child: children) {
        double child_cost = est * adapter.cost(child);
        double child_strict_cost = adapter.strict_cost(child);
        double child_est = adapter.estimate(child);
        if (idx == 0) {
            best_diff = child_strict_cost - child_cost;
        } else {
            double my_diff = (child_strict_cost + child_est * cost) - (cost + child_cost);
            if (my_diff < best_diff) {
                best_diff = my_diff;
                best_idx = idx;
            }
        }
        cost += child_cost;
        est *= child_est;
        ++idx;
    }
    return best_idx;
}

} // flow

template <typename FLOW>
struct FlowMixin {
    static double estimate_of(auto adapter, const auto &children) {
        return flow::estimate_of(adapter, children, FLOW(false));
    }
    static double estimate_of(const auto &children) {
        return estimate_of(flow::make_adapter(children), children);
    }
    static double cost_of(auto adapter, const auto &children, bool strict) {
        auto my_adapter = flow::IndirectAdapter(adapter, children);
        auto order = flow::make_index(children.size());
        FLOW::sort(my_adapter, order, strict);
        return flow::ordered_cost_of(my_adapter, order, FLOW(strict));
    }
    static double cost_of(const auto &children, bool strict) {
        return cost_of(flow::make_adapter(children), children, strict);
    }
};

class AndFlow : public FlowMixin<AndFlow> {
private:
    double _flow;
    bool _strict;
    bool _first;
public:
    AndFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()), _first(true) {}
    void add(double est) noexcept {
        _flow *= est;
        _first = false;
    }
    double flow() const noexcept {
        return _flow;
    }
    bool strict() const noexcept {
        return _strict && _first;
    }
    double estimate() const noexcept {
        return _first ? 0.0 : _flow;
    }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost += child_cost;
    }
    static void sort(auto adapter, auto &children, bool strict) {
        flow::sort<flow::MinAndCost>(adapter, children);
        if (strict && children.size() > 1) {
            size_t idx = flow::select_strict_and_child(adapter, children);
            auto the_one = std::move(children[idx]);
            for (; idx > 0; --idx) {
                children[idx] = std::move(children[idx-1]);
            }
            children[0] = std::move(the_one);
        }
    }
    static void sort(auto &children, bool strict) {
        sort(flow::make_adapter(children), children, strict);
    }
};

class OrFlow : public FlowMixin<OrFlow>{
private:
    double _flow;
    bool _strict;
    bool _first;
public:
    OrFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()), _first(true) {}
    void add(double est) noexcept {
        _flow *= (1.0 - est);
        _first = false;
    }
    double flow() const noexcept {
        return _strict ? 1.0 : _flow;
    }
    bool strict() const noexcept {
        return _strict;
    }
    double estimate() const noexcept {
        return _first ? 0.0 : (1.0 - _flow);
    }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost += child_cost;
    }
    static void sort(auto adapter, auto &children, bool strict) {
        if (!strict) {
            flow::sort<flow::MinOrCost>(adapter, children);
        }
    }
    static void sort(auto &children, bool strict) {
        sort(flow::make_adapter(children), children, strict);
    }
};

class AndNotFlow : public FlowMixin<AndNotFlow> {
private:
    double _flow;
    bool _strict;
    bool _first;
public:
    AndNotFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()), _first(true) {}
    void add(double est) noexcept {
        _flow *= _first ? est : (1.0 - est);
        _first = false;
    }
    double flow() const noexcept {
        return _flow;
    }
    bool strict() const noexcept {
        return _strict && _first;
    }
    double estimate() const noexcept {
        return _first ? 0.0 : _flow;
    }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost += child_cost;
    }
    static void sort(auto adapter, auto &children, bool) {
        flow::sort_partial<flow::MinOrCost>(adapter, children, 1);
    }
    static void sort(auto &children, bool strict) {
        sort(flow::make_adapter(children), children, strict);
    }
};

using FlowCalc = std::function<double(double)>;

template <typename FLOW>
FlowCalc flow_calc(InFlow in_flow) {
    return [flow=FLOW(in_flow)](double est) mutable noexcept {
               double next_flow = flow.flow();
               flow.add(est);
               return next_flow;
           };
}

inline FlowCalc first_flow_calc(InFlow in_flow) {
    bool first = true;
    double flow = in_flow.rate();
    return [first,flow](double est) mutable noexcept {
               double next_flow = flow;
               if (first) {
                   flow *= est;
                   first = false;
               }
               return next_flow;
           };
}

inline FlowCalc full_flow_calc(InFlow in_flow) {
    double flow = in_flow.rate();
    return [flow](double) noexcept { return flow; };
}

}
