// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/eval/gp/gp.h>
#include <limits.h>
#include <algorithm>

using namespace vespalib;
using namespace vespalib::gp;

// Inspired by the great and sometimes frustrating puzzles posed to us
// by IBM; what about automatically evolving a solution instead of
// figuring it out on our own. Turns out GP is no free lunch, but
// rather a strange and interesting adventure all of its own...

// problem: https://www.research.ibm.com/haifa/ponderthis/challenges/November2017.html
// solution: https://www.research.ibm.com/haifa/ponderthis/solutions/November2017.html

// illegal div/mod will result in 0
bool div_ok(int a, int b) {
    if ((a == INT_MIN) && (b == -1)) {
        return false;
    }
    return (b != 0);
}
int my_add(int a, int b) { return a + b; }
int my_sub(int a, int b) { return a - b; }
int my_mul(int a, int b) { return a * b; }
int my_div(int a, int b) { return div_ok(a, b) ? (a / b) : 0; }
int my_mod(int a, int b) { return div_ok(a, b) ? (a % b) : 0; }
int my_pow(int a, int b) { return pow(a,b); }
int my_and(int a, int b) { return a & b; }
int my_or(int a, int b)  { return a | b; }
int my_xor(int a, int b) { return a ^ b; }

struct Dist {
    std::vector<int> slots;
    static size_t need_slots(size_t num_outputs) {
        size_t result = 6; // z
        if (num_outputs > 1) {
            result *= 2; // y
            if (num_outputs > 2) {
                result *= 2; // x
                ASSERT_EQUAL(num_outputs, 3u);
            }
        }
        return result;
    }
    Dist(size_t num_outputs) : slots(need_slots(num_outputs), 0) {}
    void sample(int z) {
        int post_z = (size_t(z) % 6);
        ASSERT_GREATER_EQUAL(post_z, 0);
        ASSERT_LESS(post_z, 6);
        int slot = post_z;
        ASSERT_LESS(size_t(slot), slots.size());
        ++slots[slot];
    }
    void sample(int z, int y) {
        int post_y = (y & 1);
        int post_z = (size_t(z) % 6);
        ASSERT_GREATER_EQUAL(post_y, 0);
        ASSERT_GREATER_EQUAL(post_z, 0);
        ASSERT_LESS(post_y, 2);
        ASSERT_LESS(post_z, 6);
        int slot = (post_z<<1) | (post_y);
        ASSERT_LESS(size_t(slot), slots.size());
        ++slots[slot];
    }
    void sample(int z, int y, int x) {
        int post_x = (x & 1);
        int post_y = (y & 1);
        int post_z = (size_t(z) % 6);
        ASSERT_GREATER_EQUAL(post_x, 0);
        ASSERT_GREATER_EQUAL(post_y, 0);
        ASSERT_GREATER_EQUAL(post_z, 0);
        ASSERT_LESS(post_x, 2);
        ASSERT_LESS(post_y, 2);
        ASSERT_LESS(post_z, 6);
        int slot = (post_z<<2) | (post_y<<1) | (post_x);
        ASSERT_LESS(size_t(slot), slots.size());
        ++slots[slot];
    }
    size_t error() const {
        size_t err = 0;
        int expect = (216 / slots.size());
        ASSERT_EQUAL(216 % slots.size(), 0u);
        for (int cnt: slots) {
            err += (std::max(cnt, expect) - std::min(cnt, expect));
        }
        return err;
    }
};

Feedback find_weakness(const MultiFunction &fun) {
    size_t num_outputs = fun.num_outputs();
    std::vector<Dist> state(fun.num_alternatives(), Dist(num_outputs));
    for (int d1 = 1; d1 <= 6; ++d1) {
        for (int d2 = 1; d2 <= 6; ++d2) {
            for (int d3 = 1; d3 <= 6; ++d3) {
                Input input({d1, d2, d3});
                std::sort(input.begin(), input.end());
                if (fun.num_inputs() == 6) {
                    // add const values for hand-crafted case
                    input.push_back(2);
                    input.push_back(1502);
                    input.push_back(70677);
                }
                Result result = fun.execute(input);
                ASSERT_EQUAL(result.size(), state.size());
                for (size_t i = 0; i < result.size(); ++i) {
                    const Output &output = result[i];
                    switch(output.size()) {
                    case 1:
                        state[i].sample(output[0]); // z
                        break;
                    case 2:
                        state[i].sample(output[0], output[1]); // z,y
                        break;
                    default:
                        ASSERT_EQUAL(output.size(), 3u);
                        state[i].sample(output[0], output[1], output[2]); // z,y,x
                    }
                }
            }
        }
    }
    Feedback feedback;
    for (const Dist &dist: state) {
        feedback.push_back(dist.error());
    }
    return feedback;
}

OpRepo my_repo() {
    return OpRepo(find_weakness)
        .add("add", my_add)  // 1
        .add("sub", my_sub)  // 2
        .add("mul", my_mul)  // 3
        .add("div", my_div)  // 4
        .add("mod", my_mod)  // 5
        .add("pow", my_pow)  // 6
        .add("and", my_and)  // 7
        .add("or",  my_or)   // 8
        .add("xor", my_xor); // 9
}

// Featured solution (Bert Dobbelaere):
//
// d=2**(((c-a)*(c+a))/2)
//     x=(1502/d)%2
//     y=(70677/d)%2
//     z=(a+b+c)%6+1

const size_t add_id = 1;
const size_t sub_id = 2;
const size_t mul_id = 3;
const size_t div_id = 4;
const size_t pow_id = 6;

using Ref = Program::Ref;
using Op = Program::Op;

TEST("evaluating hand-crafted solution") {
    // constants are modeled as inputs
    Program prog(my_repo(), 6, 3, 2, 0);
    auto a = Ref::in(0);                   // a
    auto b = Ref::in(1);                   // b
    auto c = Ref::in(2);                   // c
    auto k1 = Ref::in(3);                  // 2
    auto k2 = Ref::in(4);                  // 1502
    auto k3 = Ref::in(5);                  // 70677
    auto _1 = prog.add_op(sub_id, c, a);   // _1 = c-a
    auto _2 = prog.add_op(add_id, c, a);   // _2 = c+a
    auto _3 = prog.add_op(mul_id, _1, _2); // _3 = (c-a)*(c+a)
    // (zero-cost forwarding, for testing)
    _1 = prog.add_forward(_1);
    _2 = prog.add_forward(_2);
    _3 = prog.add_forward(_3);
    auto _4 = prog.add_op(div_id, _3, k1); // _4 = ((c-a)*(c+a))/2
    auto d = prog.add_op(pow_id, k1, _4);  // d = 2**(((c-a)*(c+a))/2)
    auto _5 = prog.add_op(add_id, a, b);   // _5 = a+b
    // --- alt 0 (dummy outputs, for testing)
    prog.add_forward(_1);
    prog.add_forward(_2);
    prog.add_forward(_3);
    // --- alt 1 (correct output)
    auto z = prog.add_op(add_id, _5, c);   // z = a+b+c
    auto y = prog.add_op(div_id, k3, d);   // y = 70677/d
    auto x = prog.add_op(div_id, k2, d);   // x = 1502/d
    // '%2' (for x and y) and '%6+1' (for z) done outside program
    //--- verify sub-expressions
    EXPECT_EQUAL(prog.as_string(a), "i0");
    EXPECT_EQUAL(prog.as_string(k2), "i4");
    EXPECT_EQUAL(prog.as_string(d), "pow(i3,div(mul(sub(i2,i0),add(i2,i0)),i3))");
    EXPECT_EQUAL(prog.as_string(x), "div(i4,pow(i3,div(mul(sub(i2,i0),add(i2,i0)),i3)))");
    EXPECT_EQUAL(prog.as_string(y), "div(i5,pow(i3,div(mul(sub(i2,i0),add(i2,i0)),i3)))");
    EXPECT_EQUAL(prog.as_string(z), "add(add(i0,i1),i2)");
    //--- verify (expression) sizes
    EXPECT_EQUAL(prog.size_of(a), 1u);
    EXPECT_EQUAL(prog.size_of(k2), 1u);
    EXPECT_EQUAL(prog.size_of(d), 11u);
    EXPECT_EQUAL(prog.size_of(x), 13u);
    EXPECT_EQUAL(prog.size_of(y), 13u);
    EXPECT_EQUAL(prog.size_of(z), 5u);
    //--- verify costs
    EXPECT_EQUAL(prog.get_cost(0), 3u);
    EXPECT_EQUAL(prog.get_cost(1), 9u);
    //--- evaluate
    Random dummy;
    prog.handle_feedback(dummy, find_weakness(prog));
    EXPECT_EQUAL(prog.stats().weakness, 0.0);
    EXPECT_EQUAL(prog.stats().cost, 9u);
    EXPECT_EQUAL(prog.stats().alt, 1u);
}

void maybe_newline(bool &partial_line) {
    if (partial_line) {
        fprintf(stderr, "\n");
        partial_line = false;
    }
}

Program try_evolve(const Params &params, size_t max_idle, const Program *program = nullptr) {
    Population population(params, my_repo(), Random().make_seed());
    if (program != nullptr) {
        population.init(*program);
    }
    bool partial_line = false;
    size_t ticks = 0;
    size_t sample_tick = ticks;
    Program::Stats best_sample = population._programs[0].stats();
    while (!SignalHandler::INT.check() &&
           ((best_sample.weakness > 0) ||
            ((ticks - sample_tick) < max_idle)))
    {
        ++ticks;
        population.tick();
        if ((ticks % 500) == 0) {
            maybe_newline(partial_line);
            population.print_stats();
        } else if ((ticks % 10) == 0) {
            fprintf(stderr, ".");
            partial_line = true;
        }
        Program::Stats sample = population._programs[0].stats();
        best_sample.born = sample.born;
        if (sample < best_sample) {
            best_sample = sample;
            sample_tick = ticks;
        }
    }
    if (SignalHandler::INT.check()) {
        fprintf(stderr, "<INT>\n");
        SignalHandler::INT.clear();
    }
    maybe_newline(partial_line);
    Program::Stats best = population._programs[0].stats();
    fprintf(stderr, "best stats after %zu ticks: (weakness=%g,cost=%zu)\n",
            ticks, best.weakness, best.cost);
    return population._programs[0];
}

// best stats: (weakness=0,cost=9)
// x(size=21): mod(add(div(add(i2,i0),i0),and(mod(mul(i1,add(i1,add(i2,i0))),add(i2,i0)),i2)),i2)
// y(size=13): sub(mod(mul(i1,add(i1,add(i2,i0))),add(i2,i0)),i2)
// z(size=5): add(i1,add(i2,i0))

TEST("trying to evolve a solution automatically") {
    fprintf(stderr, "training f(a,b,c) -> (z)...\n");
    Program best_z = try_evolve(Params(3, 1, 8, 8, 8), 10 * 1000);
    fprintf(stderr, "training f(a,b,c) -> (z,y)...\n");
    Program best_zy = try_evolve(Params(3, 2, 8, 8, 8), 100 * 1000, &best_z);
    fprintf(stderr, "training f(a,b,c) -> (z,y,x)...\n");
    Program best = try_evolve(Params(3, 3, 8, 8, 8), 1000 * 1000 * 1000, &best_zy);
    auto refs = best.get_refs(best.stats().alt);
    fprintf(stderr, "x(size=%zu): %s\n", best.size_of(refs[2]), best.as_string(refs[2]).c_str());
    fprintf(stderr, "y(size=%zu): %s\n", best.size_of(refs[1]), best.as_string(refs[1]).c_str());
    fprintf(stderr, "z(size=%zu): %s\n", best.size_of(refs[0]), best.as_string(refs[0]).c_str());
}

TEST_MAIN() {
    SignalHandler::INT.hook();
    TEST_RUN_ALL();
}
