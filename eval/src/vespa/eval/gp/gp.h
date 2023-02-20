// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <random>
#include <chrono>
#include <assert.h>

namespace vespalib::gp {

using Value = int; // all input/output/intermediate values have this type (should be template parameter)
using Weakness = double;

// high level training parameters
struct Params {
    size_t in_cnt;  // # function inputs
    size_t out_cnt; // # function outputs
    size_t op_cnt;  // # internal operations per individual
    size_t alt_cnt; // # alternative outputs
    size_t pop_cnt; // # individuals in population
    Params(size_t in_cnt_in, size_t out_cnt_in,
           size_t op_cnt_in, size_t alt_cnt_in,
           size_t pop_cnt_in)
        : in_cnt(in_cnt_in), out_cnt(out_cnt_in),
          op_cnt(op_cnt_in), alt_cnt(alt_cnt_in),
          pop_cnt(pop_cnt_in) {}
};

using Input = std::vector<Value>;       // input values
using Output = std::vector<Value>;      // output values
using Result = std::vector<Output>;     // alternative output values
using Feedback = std::vector<Weakness>; // weakness per result alternative

// simple random generator
struct Random {
    std::mt19937 gen;
    Random(int seed) : gen(seed) {}
    Random() : Random(std::chrono::system_clock::now().time_since_epoch().count()) {}
    int get(int min, int max) {
        std::uniform_int_distribution<int> dist(min, max);
        return dist(gen);
    }
    int make_seed() {
        return get(std::numeric_limits<int>::lowest(),
                   std::numeric_limits<int>::max());
    }
};

// Multiple alternatives for a function taking multiple inputs
// producing multiple outputs
struct MultiFunction {
    virtual size_t num_inputs() const = 0;
    virtual size_t num_outputs() const = 0;
    virtual size_t num_alternatives() const = 0;
    virtual Result execute(const Input &input) const = 0;
    virtual ~MultiFunction() {}
};

// simulated individual representing a multifunction
struct Sim : public MultiFunction {
    virtual void handle_feedback(Random &rnd, const Feedback &feedback) = 0;
};

// available operations
struct OpRepo {
    using feedback_fun = Feedback (*)(const MultiFunction &multi_fun);
    using value_op2 = Value (*)(Value lhs, Value rhs);
    static Value forward_op(Value lhs, Value) { return lhs; }
    struct Entry {
        vespalib::string name;
        value_op2 fun;
        size_t cost;
        Entry(const vespalib::string &name_in, value_op2 fun_in, size_t cost_in) noexcept
            : name(name_in), fun(fun_in), cost(cost_in) {}
    };
    feedback_fun _find_weakness;
    std::vector<Entry> _list;
    OpRepo(feedback_fun find_weakness_in)
        : _find_weakness(find_weakness_in), _list()
    {
        _list.emplace_back("forward", forward_op, 0);
    }
    OpRepo &add(const vespalib::string &name, value_op2 fun) {
        _list.emplace_back(name, fun, 1);
        return *this;
    }
    const vespalib::string &name_of(size_t op) const { return _list[op].name; }
    size_t cost_of(size_t op) const { return _list[op].cost; }
    size_t max_op() const { return (_list.size() - 1); }
    void find_weakness(Random &rnd, Sim &sim) const {
        sim.handle_feedback(rnd, _find_weakness(sim));
    }
    Value perform(size_t op, Value lhs, Value rhs) const {
        return _list[op].fun(lhs, rhs);
    }
};

// specific simulated individual implementation
struct Program : public Sim {
    class Ref {
    private:
        int idx; // negative: input, zero/positive: operation_result
        Ref(int idx_in) : idx(idx_in) {}
    public:
        bool is_input() const { return (idx < 0); }
        bool is_operation() const { return (idx >= 0); }
        size_t in_idx() const { return -(idx + 1); }
        size_t op_idx() const { return idx; }
        bool operator<(const Ref &rhs) const { return (idx < rhs.idx); }
        static Ref in(size_t idx_in) { return Ref(-int(idx_in + 1)); }
        static Ref op(size_t idx_in) { return Ref(idx_in); }
        static Ref nop() { return in(0); }
        static Ref rnd(Random &rnd_in, size_t in_cnt, size_t op_cnt) {
            return Ref(rnd_in.get(-in_cnt, op_cnt - 1));
        }
    };
    struct Op {
        size_t code;
        Ref    lhs;
        Ref    rhs;
        Op(size_t code_in, Ref lhs_in, Ref rhs_in) noexcept
            : code(code_in), lhs(lhs_in), rhs(rhs_in) {}
    };
    struct Stats {
        Weakness weakness;
        size_t   cost;
        size_t   born;
        size_t   alt;
        Stats(size_t gen) noexcept : weakness(0.0), cost(0), born(gen), alt(0) {}
        Stats(Weakness weakness_in, size_t cost_in, size_t born_in, size_t alt_in) noexcept
            : weakness(weakness_in), cost(cost_in), born(born_in), alt(alt_in) {}
        bool operator<(const Stats &rhs) const {
            if (weakness != rhs.weakness) {
                return (weakness < rhs.weakness);
            }
            if (cost != rhs.cost) {
                return (cost < rhs.cost);
            }
            return (born > rhs.born); // younger is better
        }
    };

    OpRepo           _repo;
    Stats            _stats;
    double           _waste;
    size_t           _in_cnt;
    size_t           _out_cnt;
    size_t           _alt_cnt;
    std::vector<Op>  _program;
    size_t           _frozen;
    std::vector<Ref> _bound;

    size_t get_alt_size() const { return (_out_cnt - _bound.size()); }
    size_t get_alt_offset(size_t alt) const {
        assert(alt < _alt_cnt);
        size_t r_offset = (_alt_cnt - alt) * get_alt_size();
        assert(_program.size() >= r_offset);
        return (_program.size() - r_offset);
    }

    void assert_valid(Ref ref, size_t limit) const;

    size_t rnd_op(Random &rnd) { return rnd.get(0, _repo.max_op()); }
    Ref rnd_ref(Random &rnd, size_t limit) { return Ref::rnd(rnd, _in_cnt, limit); }

    Program(Program &&) noexcept;
    Program &operator=(Program &&) noexcept;
    Program(const Program &);
    Program &operator=(const Program &) = delete;
    ~Program() override;

    Program(const OpRepo &repo, size_t in_cnt, size_t out_cnt, size_t alt_cnt, size_t gen);
    Ref add_op(size_t code, Ref lhs, Ref rhs);
    Ref add_forward(Ref ref);

    void init(const Program &src);
    void grow(Random &rnd, size_t op_cnt);
    void mutate(Random &rnd, size_t mut_idx);
    void mutate(Random &rnd);
    void reborn(size_t gen) { _stats.born = gen; }
    const Stats &stats() const { return _stats; }
    bool operator<(const Program &rhs) const {
        return (stats() < rhs.stats() ||
                ((_waste < rhs._waste) && !(rhs.stats() < stats())));
    }

    std::vector<Ref> get_refs(size_t alt) const;
    std::vector<bool> get_used_ops(size_t alt) const;
    size_t get_cost(size_t alt) const;

    size_t size_of(Ref ref) const;
    vespalib::string as_string(Ref ref) const;

    // implementation of the Sim interface
    size_t num_inputs() const override { return _in_cnt; }
    size_t num_outputs() const override { return _out_cnt; }
    size_t num_alternatives() const override { return _alt_cnt; }
    Result execute(const Input &input) const override;
    void handle_feedback(Random &rnd, const Feedback &feedback) override;
};

struct Population
{
    Random _rnd;
    size_t _gen;
    Params _params;
    OpRepo _repo;
    std::vector<Program> _programs;

    void grow(size_t cnt);
    void print_stats() const;

    Population(const Params &params, const OpRepo &repo, int seed)
        : _rnd(seed),
          _gen(0),
          _params(params),
          _repo(repo),
          _programs()
    {
        grow(1);
    }
    Program mutate(const Program &a);
    void init(const Program &program);
    void tick();
};

} // namespace vespalib::gp
