// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gp.h"
#include <algorithm>
#include <vespa/vespalib/util/stringfmt.h>
#include <map>

namespace vespalib::gp {

namespace {

Value get(const Input &input, const std::vector<Value> &values, Program::Ref ref) {
    return ref.is_input() ? input[ref.in_idx()] : values[ref.op_idx()];
}

size_t get(const std::vector<size_t> &sizes, Program::Ref ref) {
    return ref.is_input() ? 1 : sizes[ref.op_idx()];
}

Program::Ref map(const std::map<Program::Ref,Program::Ref> &ref_map, Program::Ref ref) {
    if (ref.is_input()) {
        return ref;
    }
    auto pos = ref_map.find(ref);
    assert(pos != ref_map.end());
    return pos->second;
}

} // namespace vespalib::gp::<unnamed>

Program::Program(Program &&) noexcept = default;
Program & Program::operator=(Program &&) noexcept = default;
Program::Program(const Program &) = default;
Program::~Program() = default;

Program::Program(const OpRepo &repo, size_t in_cnt, size_t out_cnt, size_t alt_cnt, size_t gen)
    : _repo(repo), _stats(gen), _waste(0.0),
      _in_cnt(in_cnt), _out_cnt(out_cnt), _alt_cnt(alt_cnt),
      _program(), _frozen(0), _bound()
{}

void
Program::assert_valid(Ref ref, size_t limit) const
{
    assert(ref.is_input() != ref.is_operation());
    if (ref.is_input()) {
        assert(ref.in_idx() < _in_cnt);
    }
    if (ref.is_operation()) {
        assert(ref.op_idx() < limit);
    }
}

Program::Ref
Program::add_op(size_t code, Ref lhs, Ref rhs)
{
    size_t op_idx = _program.size();
    assert(code <= _repo.max_op());
    assert_valid(lhs, op_idx);
    assert_valid(rhs, op_idx);
    _program.emplace_back(code, lhs, rhs);
    return Ref::op(op_idx);
}

Program::Ref
Program::add_forward(Ref ref)
{
    return add_op(0, ref, Ref::nop());
}

void
Program::init(const Program &src)
{
    assert(src._out_cnt < _out_cnt);
    std::map<Ref,Ref> ref_map;
    auto used = src.get_used_ops(src.stats().alt);
    for (size_t i = 0; i < used.size(); ++i) {
        if (used[i]) {
            const Op &op = src._program[i];
            if (op.code == 0) { // forward
                auto res = ref_map.emplace(Ref::op(i), map(ref_map, op.lhs));
                assert(res.second);
            } else {
                auto res = ref_map.emplace(Ref::op(i), Ref::op(_program.size()));
                assert(res.second);
                _program.emplace_back(op.code,
                                      map(ref_map, op.lhs),
                                      map(ref_map, op.rhs));
            }
        }
    }
    _frozen = _program.size();
    for (Ref ref: src.get_refs(src.stats().alt)) {
        _bound.push_back(map(ref_map, ref));
    }
}

void
Program::grow(Random &rnd, size_t op_cnt)
{
    for (size_t i = 0; i < op_cnt; ++i) {
        size_t op_idx = _program.size();
        add_op(rnd_op(rnd),
               rnd_ref(rnd, op_idx),
               rnd_ref(rnd, op_idx));
    }
    size_t prefix = _program.size();
    size_t suffix = _alt_cnt * get_alt_size();
    for (size_t i = 0; i < suffix; ++i) {
        add_op(rnd_op(rnd),
               rnd_ref(rnd, prefix),
               rnd_ref(rnd, prefix));
    }
}

void
Program::mutate(Random &rnd, size_t mut_idx)
{
    size_t prefix = get_alt_offset(0);
    Op &op = _program[mut_idx];
    size_t sel = rnd.get(0,2);
    if (sel == 0) {
        op.code = rnd_op(rnd);
    } else if (sel == 1) {
        op.lhs = rnd_ref(rnd, std::min(mut_idx, prefix));
    } else {
        assert(sel == 2);
        op.rhs = rnd_ref(rnd, std::min(mut_idx, prefix));
    }
}

void
Program::mutate(Random &rnd)
{
    assert(_frozen < _program.size());
    mutate(rnd, rnd.get(_frozen, _program.size() - 1));
}

std::vector<Program::Ref>
Program::get_refs(size_t alt) const
{
    std::vector<Ref> refs;
    refs.reserve(_out_cnt);
    refs = _bound;
    size_t offset = get_alt_offset(alt);
    while (refs.size() < _out_cnt) {
        refs.push_back(Ref::op(offset++));
    }
    return refs;
}

std::vector<bool>
Program::get_used_ops(size_t alt) const
{
    std::vector<bool> used(_program.size(), false);
    std::vector<Ref> todo = get_refs(alt);
    while (!todo.empty()) {
        Ref ref = todo.back();
        todo.pop_back();
        if (ref.is_operation() && !used[ref.op_idx()]) {
            const Op &op = _program[ref.op_idx()];
            todo.push_back(op.lhs);
            if (op.code > 0) {
                todo.push_back(op.rhs);
            }
            used[ref.op_idx()] = true;
        }
    }
    return used;
}

size_t
Program::get_cost(size_t alt) const
{
    size_t cost = 0;
    auto used = get_used_ops(alt);
    for (size_t i = 0; i < used.size(); ++i) {
        if (used[i]) {
            cost += _repo.cost_of(_program[i].code);
        }
    }
    return cost;
}

size_t
Program::size_of(Ref ref) const
{
    assert_valid(ref, _program.size());
    if (ref.is_input()) {
        return 1;
    }
    std::vector<size_t> sizes;
    for (size_t i = 0; i <= ref.op_idx(); ++i) {
        const Op &op = _program[i];
        if (op.code == 0) {
            sizes.push_back(get(sizes, op.lhs)); // forward
        } else {
            sizes.push_back(1 + get(sizes, op.lhs) + get(sizes, op.rhs));
        }
    }
    return sizes.back();
}

vespalib::string
Program::as_string(Ref ref) const
{
    assert_valid(ref, _program.size());
    size_t expr_size = size_of(ref);
    if (expr_size > 9000) {
        // its over 9000!
        return vespalib::make_string("expr(%zu nodes)", expr_size);
    } else if (ref.is_input()) {
        return vespalib::make_string("i%zu", ref.in_idx());
    } else {
        const Op &my_op = _program[ref.op_idx()];
        if (my_op.code == 0) {
            return as_string(my_op.lhs); // forward
        } else {
            return vespalib::make_string("%s(%s,%s)", _repo.name_of(my_op.code).c_str(),
                                         as_string(my_op.lhs).c_str(), as_string(my_op.rhs).c_str());
        }
    }
}

Result
Program::execute(const Input &input) const
{
    Result result;
    std::vector<Value> values;
    size_t prefix = get_alt_offset(0);
    values.reserve(prefix);
    size_t idx = 0;
    for (; idx < prefix; ++idx) {
        const Op &op = _program[idx];
        values.push_back(_repo.perform(op.code,
                                       get(input, values, op.lhs),
                                       get(input, values, op.rhs)));
    }
    for (size_t i = 0; i < _alt_cnt; ++i) {
        std::vector<Value> out;
        out.reserve(_out_cnt);
        for (Ref ref: _bound) {
            out.push_back(get(input, values, ref));
        }
        while (out.size() < _out_cnt) {
            const Op &op = _program[idx++];
            out.push_back(_repo.perform(op.code,
                                        get(input, values, op.lhs),
                                        get(input, values, op.rhs)));
        }
        result.push_back(out);
    }
    assert(idx == _program.size());
    return result;
}

void
Program::handle_feedback(Random &rnd, const Feedback &feedback)
{
    assert(feedback.size() == _alt_cnt);
    std::vector<Stats> my_stats;
    my_stats.reserve(_alt_cnt);
    for (size_t i = 0; i < _alt_cnt; ++i) {
        my_stats.emplace_back(feedback[i], get_cost(i), _stats.born, i);
    }
    std::sort(my_stats.begin(), my_stats.end());
    _stats = my_stats[0];
    for (size_t i = 1; i < my_stats.size(); ++i) {
        if ((i + 1) == my_stats.size()) { // worst
            size_t len = get_alt_size();
            size_t src = get_alt_offset(my_stats.front().alt);
            size_t dst = get_alt_offset(my_stats.back().alt);
            for (size_t j = 0; j < len; ++j) {
                _program[dst + j] = _program[src + j];
            }
            mutate(rnd, rnd.get(dst, dst + len - 1));
        } else { // not best, not worst; mediocre
            double my_waste = (my_stats[i].weakness + 1) *
                              (my_stats[i].cost + 1);
            _waste = std::min(my_waste, (i == 1) ? my_waste : _waste);
        }
    }
}

void
Population::grow(size_t cnt)
{
    while (_programs.size() < cnt) {
        _programs.emplace_back(_repo, _params.in_cnt, _params.out_cnt, _params.alt_cnt, _gen);
        _programs.back().grow(_rnd, _params.op_cnt);
        _repo.find_weakness(_rnd, _programs.back());
    }
    std::sort(_programs.begin(), _programs.end());
}

void
Population::print_stats() const
{
    const Program::Stats &best = _programs.front().stats();
    const Program::Stats &worst = _programs.back().stats();
    fprintf(stderr, "[%zu] best(weakness=%g,cost=%zu,age=%zu), "
            "worst(weakness=%g,cost=%zu,age=%zu)\n", _gen,
            best.weakness, best.cost, (_gen - best.born),
            worst.weakness, worst.cost, (_gen - worst.born));
}

Program
Population::mutate(const Program &a)
{
    Program new_prog = a;
    do {
        new_prog.mutate(_rnd);
    } while(_rnd.get(0,99) < 80);
    new_prog.reborn(_gen);
    return new_prog;
}

void
Population::init(const Program &program)
{
    _programs.clear();
    _programs.emplace_back(_repo, _params.in_cnt, _params.out_cnt, _params.alt_cnt, _gen);
    _programs.back().init(program);
    _programs.back().grow(_rnd, _params.op_cnt);
    _repo.find_weakness(_rnd, _programs.back());
}

void
Population::tick()
{
    ++_gen;
    while (_programs.size() > 1) {
        _programs.pop_back();
    }
    while (_programs.size() < _params.pop_cnt) {
        _programs.push_back(mutate(_programs[0]));
        _repo.find_weakness(_rnd, _programs.back());
    }
    std::sort(_programs.begin(), _programs.end());
}

} // namespace vespalib::gp
