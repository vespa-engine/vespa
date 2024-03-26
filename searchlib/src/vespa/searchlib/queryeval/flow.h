// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/vespalib/util/small_vector.h>
#include <cstddef>
#include <algorithm>
#include <functional>
#include <limits>

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
    constexpr auto operator <=>(const FlowStats &rhs) const noexcept = default;
    static constexpr FlowStats from(auto adapter, const auto &child) noexcept {
        return {adapter.estimate(child), adapter.cost(child), adapter.strict_cost(child)};
    }
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

// estimate the cost of evaluating a strict child in a non-strict context
inline double forced_strict_cost(double estimate, double strict_cost, double rate) {
    return 0.2 * (rate - estimate) + strict_cost;
}

// estimate the absolute cost of evaluating a child with a specific in flow
inline double min_child_cost(InFlow in_flow, const FlowStats &stats, bool allow_force_strict) {
    if (in_flow.strict()) {
        return stats.strict_cost;
    }
    if (!allow_force_strict) {
        return stats.cost * in_flow.rate();
    }
    return std::min(forced_strict_cost(stats.estimate, stats.strict_cost, in_flow.rate()),
                    stats.cost * in_flow.rate());
}

template <typename ADAPTER, typename T>
double estimate_of_and(ADAPTER adapter, const T &children) {
    double flow = children.empty() ? 0.0 : adapter.estimate(children[0]);
    for (size_t i = 1; i < children.size(); ++i) {
        flow *= adapter.estimate(children[i]);
    }
    return flow;
}

template <typename ADAPTER, typename T>
double estimate_of_or(ADAPTER adapter, const T &children) {
    double flow = 1.0;
    for (const auto &child: children) {
        flow *= (1.0 - adapter.estimate(child));
    }
    return (1.0 - flow);
}

template <typename ADAPTER, typename T>
double estimate_of_and_not(ADAPTER adapter, const T &children) {
    double flow = children.empty() ? 0.0 : adapter.estimate(children[0]);
    for (size_t i = 1; i < children.size(); ++i) {
        flow *= (1.0 - adapter.estimate(children[i]));
    }
    return flow;
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
double ordered_cost_of(ADAPTER adapter, const T &children, F flow, bool allow_force_strict) {
    double total_cost = 0.0;
    for (const auto &child: children) {
        FlowStats stats(adapter.estimate(child), adapter.cost(child), adapter.strict_cost(child));
        double child_cost = min_child_cost(InFlow(flow.strict(), flow.flow()), stats, allow_force_strict);
        flow.update_cost(total_cost, child_cost);
        flow.add(stats.estimate);
    }
    return total_cost;
}

size_t select_strict_and_child(auto adapter, const auto &children) {
    double est = 1.0;
    double cost = 0.0;
    size_t best_idx = 0;
    double best_diff = std::numeric_limits<double>::max();
    for (size_t idx = 0; idx < children.size(); ++idx) {
        auto child = FlowStats::from(adapter, children[idx]);
        double child_abs_cost = est * child.cost;
        double my_diff = (child.strict_cost + child.estimate * cost) - (cost + child_abs_cost);
        if (my_diff < best_diff) {
            best_diff = my_diff;
            best_idx = idx;
        }
        cost += child_abs_cost;
        est *= child.estimate;
    }
    return best_idx;
}

auto select_forced_strict_and_child(auto adapter, const auto &children, size_t first) {
    double est = 1.0;
    double cost = 0.0;
    size_t best_idx = 0;
    double best_diff = std::numeric_limits<double>::max();
    for (size_t idx = 0; idx < first && idx < children.size(); ++idx) {
        est *= adapter.estimate(children[idx]);
    }
    for (size_t idx = first; idx < children.size(); ++idx) {
        auto child = FlowStats::from(adapter, children[idx]);
        double child_abs_cost = est * child.cost;
        double forced_cost = forced_strict_cost(child.estimate, child.strict_cost, est);
        double my_diff = (forced_cost + child.estimate * cost) - (cost + child_abs_cost);
        if (my_diff < best_diff) {
            best_diff = my_diff;
            best_idx = idx;
        }
        cost += child_abs_cost;
        est *= child.estimate;
    }
    return std::make_pair(best_idx, best_diff);
}

} // flow

template <typename FLOW>
struct FlowMixin {
    static double cost_of(auto adapter, const auto &children, bool strict) {
        auto my_adapter = flow::IndirectAdapter(adapter, children);
        auto order = flow::make_index(children.size());
        FLOW::sort(my_adapter, order, strict);
        return flow::ordered_cost_of(my_adapter, order, FLOW(strict), false);
    }
    static double cost_of(const auto &children, bool strict) {
        return cost_of(flow::make_adapter(children), children, strict);
    }
};

class AndFlow : public FlowMixin<AndFlow> {
private:
    double _flow;
    bool _strict;
public:
    AndFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()) {}
    void add(double est) noexcept {
        _flow *= est;
        _strict = false;
    }
    double flow() const noexcept { return _flow; }
    bool strict() const noexcept { return _strict; }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost += child_cost;
    }
    static double estimate_of(auto adapter, const auto &children) {
        return flow::estimate_of_and(adapter, children);
    }
    static double estimate_of(const auto &children) {
        return estimate_of(flow::make_adapter(children), children);
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
public:
    OrFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()) {}
    void add(double est) noexcept {
        if (!_strict) {
            _flow *= (1.0 - est);
        }
    }
    double flow() const noexcept { return _flow; }
    bool strict() const noexcept { return _strict; }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost += child_cost;
    }
    static double estimate_of(auto adapter, const auto &children) {
        return flow::estimate_of_or(adapter, children);
    }
    static double estimate_of(const auto &children) {
        return estimate_of(flow::make_adapter(children), children);
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
        if (_first) {
            _flow *= est;
            _strict = false;
            _first = false;
        } else {
            _flow *= (1.0 - est);            
        }
    }
    double flow() const noexcept { return _flow; }
    bool strict() const noexcept { return _strict; }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost += child_cost;
    }
    static double estimate_of(auto adapter, const auto &children) {
        return flow::estimate_of_and_not(adapter, children);
    }
    static double estimate_of(const auto &children) {
        return estimate_of(flow::make_adapter(children), children);
    }
    static void sort(auto adapter, auto &children, bool) {
        flow::sort_partial<flow::MinOrCost>(adapter, children, 1);
    }
    static void sort(auto &children, bool strict) {
        sort(flow::make_adapter(children), children, strict);
    }
};

class RankFlow : public FlowMixin<RankFlow> {
private:
    double _flow;
    bool _strict;
    bool _first;
public:
    RankFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()), _first(true) {}
    void add(double) noexcept {
        _flow = 0.0;
        _strict = false;
        _first = false;
    }
    double flow() const noexcept { return _flow; }
    bool strict() const noexcept { return _strict; }
    void update_cost(double &total_cost, double child_cost) noexcept {
        if (_first) {
            total_cost += child_cost;
        }
    };
    static double estimate_of(auto adapter, const auto &children) {
        return children.empty() ? 0.0 : adapter.estimate(children[0]);
    }
    static double estimate_of(const auto &children) {
        return estimate_of(flow::make_adapter(children), children);
    }
    static void sort(auto, auto &, bool) {}
    static void sort(auto &, bool) {}
};

class BlenderFlow : public FlowMixin<BlenderFlow> {
private:
    double _flow;
    bool _strict;
public:
    BlenderFlow(InFlow flow) noexcept : _flow(flow.rate()), _strict(flow.strict()) {}
    void add(double) noexcept {}
    double flow() const noexcept { return _flow; }
    bool strict() const noexcept { return _strict; }
    void update_cost(double &total_cost, double child_cost) noexcept {
        total_cost = std::max(total_cost, child_cost);
    };
    static double estimate_of(auto adapter, const auto &children) {
        return flow::estimate_of_or(adapter, children);
    }
    static double estimate_of(const auto &children) {
        return estimate_of(flow::make_adapter(children), children);
    }
    static void sort(auto, auto &, bool) {}
    static void sort(auto &, bool) {}
};

// type-erased flow wrapper
class AnyFlow {
private:
    struct API {
        virtual void add(double est) noexcept = 0;
        virtual double flow() const noexcept = 0;
        virtual bool strict() const noexcept = 0;
        virtual void update_cost(double &total_cost, double child_cost) noexcept = 0;
        virtual ~API() = default;
    };
    template <typename FLOW> struct Wrapper final : API {
        FLOW _flow;
        Wrapper(InFlow in_flow) noexcept : _flow(in_flow) {}
        void add(double est) noexcept override { _flow.add(est); }
        double flow() const noexcept override { return _flow.flow(); }
        bool strict() const noexcept override { return _flow.strict(); }
        void update_cost(double &total_cost, double child_cost) noexcept override {
            _flow.update_cost(total_cost, child_cost);
        }
        ~Wrapper() = default;
    };
    alignas(8) char _space[24];
    API &api() noexcept { return *reinterpret_cast<API*>(_space); }
    const API &api() const noexcept { return *reinterpret_cast<const API*>(_space); }
    template <typename FLOW> struct type_tag{};
    template <typename FLOW> AnyFlow(InFlow in_flow, type_tag<FLOW>) noexcept {
        using stored_type = Wrapper<FLOW>;
        static_assert(alignof(stored_type) <= 8);
        static_assert(sizeof(stored_type) <= sizeof(_space));
        API *upcasted = ::new (static_cast<void*>(_space)) stored_type(in_flow);
        (void) upcasted;
        assert(static_cast<void*>(upcasted) == static_cast<void*>(_space));
    }
public:
    AnyFlow() = delete;
    AnyFlow(AnyFlow &&) = delete;
    AnyFlow(const AnyFlow &) = delete;
    AnyFlow &operator=(AnyFlow &&) = delete;
    AnyFlow &operator=(const AnyFlow &) = delete;
    template <typename FLOW> static AnyFlow create(InFlow in_flow) noexcept {
        return AnyFlow(in_flow, type_tag<FLOW>());
    }
    void add(double est) noexcept { api().add(est); }
    double flow() const noexcept { return api().flow(); }
    bool strict() const noexcept { return api().strict(); }
    void update_cost(double &total_cost, double child_cost) noexcept {
        api().update_cost(total_cost, child_cost);
    }
};

}
