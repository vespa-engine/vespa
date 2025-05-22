// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/eval/eval/node_traverser.h>
#include <vespa/eval/eval/value_codec.h>
#include <set>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/check_type.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib::eval;
using namespace vespalib::eval::nodes;
using vespalib::eval::test::GenSpec;

std::vector<std::string> params({"x", "y", "z", "w"});

double as_number(const Function &f) {
    auto& number = dynamic_cast<const Number&>(f.root());
    return number.value();
}

std::string as_string(const Function &f) {
    auto& string = dynamic_cast<const String&>(f.root());
    return string.value();
}

struct OperatorLayer {
    Operator::Order order;
    std::vector<std::string> op_names;
};

Operator_UP create_op(std::string name) {
    Operator_UP op = OperatorRepo::instance().create(name);
    EXPECT_TRUE(op.get() != nullptr);
    EXPECT_EQ(name, op->op_str());
    return op;
}

void verify_operator_binding_order(std::initializer_list<OperatorLayer> layers) {
    std::set<std::string> seen_names;
    int layer_idx = 0;
    for (OperatorLayer layer: layers) {
        ++layer_idx;
        for (std::string op_name: layer.op_names) {
            seen_names.insert(op_name);
            int other_layer_idx = 0;
            for (OperatorLayer other_layer: layers) {
                ++other_layer_idx;
                for (std::string other_op_name: other_layer.op_names) {
                    Operator_UP op = create_op(op_name);
                    Operator_UP other_op = create_op(other_op_name);
                    bool do_op_before_other_op = (layer_idx < other_layer_idx)
                                                 || ((layer_idx == other_layer_idx)
                                                         && (layer.order == Operator::Order::LEFT));
                    EXPECT_EQ(do_op_before_other_op, op->do_before(*other_op)) <<
                        "error: left operator '" << op->op_str() <<
                        "' should " << (do_op_before_other_op ? "" : "not ") <<
                        "bind before right operator '" << other_op->op_str() << "'";
                }
            }
        }
    }
    auto all_names = OperatorRepo::instance().get_names();
    for (auto name: all_names) {
        EXPECT_EQ(1u, seen_names.count(name)) <<
            "error: operator '" << name << "' not verified by binding order test";
    }
}

bool verify_string(const std::string &str, const std::string &expr) {
    bool ok = true;
    EXPECT_EQ(str, as_string(*Function::parse(params, expr))) << (ok = false, "");
    EXPECT_EQ(expr, Function::parse(params, expr)->dump()) << (ok = false, "");
    return ok;
}

void verify_error(const std::string &expr, const std::string &expected_error) {
    SCOPED_TRACE(expr);
    auto function = Function::parse(params, expr);
    EXPECT_TRUE(function->has_error());
    EXPECT_EQ(expected_error, function->get_error());
}

void verify_parse(const std::string &expr, const std::string &expect) {
    SCOPED_TRACE(expr);
    auto function = Function::parse(expr);
    EXPECT_TRUE(!function->has_error());
    EXPECT_EQ(function->dump_as_lambda(), expect);
}

TEST(FunctionTest, require_that_scientific_numbers_can_be_parsed)
{
    EXPECT_EQ(1.0,     as_number(*Function::parse(params, "1")));
    EXPECT_EQ(2.5,     as_number(*Function::parse(params, "2.5")));
    EXPECT_EQ(100.0,   as_number(*Function::parse(params, "100")));
    EXPECT_EQ(0.01,    as_number(*Function::parse(params, "0.01")));
    EXPECT_EQ(1.05e5,  as_number(*Function::parse(params, "1.05e5")));
    EXPECT_EQ(3e7,     as_number(*Function::parse(params, "3e7")));
    EXPECT_EQ(1.05e5,  as_number(*Function::parse(params, "1.05e+5")));
    EXPECT_EQ(3e7,     as_number(*Function::parse(params, "3e+7")));
    EXPECT_EQ(1.05e-5, as_number(*Function::parse(params, "1.05e-5")));
    EXPECT_EQ(3e-7,    as_number(*Function::parse(params, "3e-7")));
    EXPECT_EQ(1.05e5,  as_number(*Function::parse(params, "1.05E5")));
    EXPECT_EQ(3e7,     as_number(*Function::parse(params, "3E7")));
    EXPECT_EQ(1.05e5,  as_number(*Function::parse(params, "1.05E+5")));
    EXPECT_EQ(3e7,     as_number(*Function::parse(params, "3E+7")));
    EXPECT_EQ(1.05e-5, as_number(*Function::parse(params, "1.05E-5")));
    EXPECT_EQ(3e-7,    as_number(*Function::parse(params, "3E-7")));
}

TEST(FunctionTest, require_that_true_and_false_can_be_parsed)
{
    EXPECT_EQ(1.0, as_number(*Function::parse(params, "true")));
    EXPECT_EQ(0.0, as_number(*Function::parse(params, "false")));
}

TEST(FunctionTest, require_that_number_parsing_does_not_eat_plus_and_minus_operators)
{
    EXPECT_EQ("(((1+2)+3)+4)", Function::parse(params, "1+2+3+4")->dump());
    EXPECT_EQ("(((1-2)-3)-4)", Function::parse(params, "1-2-3-4")->dump());
    EXPECT_EQ("(((1+x)+3)+y)", Function::parse(params, "1+x+3+y")->dump());
    EXPECT_EQ("(((1-x)-3)-y)", Function::parse(params, "1-x-3-y")->dump());
}

TEST(FunctionTest, require_that_symbols_can_be_parsed)
{
    EXPECT_EQ("x", Function::parse(params, "x")->dump());
    EXPECT_EQ("y", Function::parse(params, "y")->dump());
    EXPECT_EQ("z", Function::parse(params, "z")->dump());
}

TEST(FunctionTest, require_that_parenthesis_can_be_parsed)
{
    EXPECT_EQ("x", Function::parse(params, "(x)")->dump());
    EXPECT_EQ("x", Function::parse(params, "((x))")->dump());
    EXPECT_EQ("x", Function::parse(params, "(((x)))")->dump());
}

TEST(FunctionTest, require_that_strings_are_parsed_and_dumped_correctly)
{
    EXPECT_TRUE(verify_string("foo", "\"foo\""));
    EXPECT_TRUE(verify_string("", "\"\""));
    EXPECT_TRUE(verify_string(" ", "\" \""));
    EXPECT_TRUE(verify_string(">\\<", "\">\\\\<\""));
    EXPECT_TRUE(verify_string(">\"<", "\">\\\"<\""));
    EXPECT_TRUE(verify_string(">\t<", "\">\\t<\""));
    EXPECT_TRUE(verify_string(">\n<", "\">\\n<\""));
    EXPECT_TRUE(verify_string(">\r<", "\">\\r<\""));
    EXPECT_TRUE(verify_string(">\f<", "\">\\f<\""));
    for (int c = 0; c < 256; ++c) {
        std::string raw_expr = vespalib::make_string("\"%c\"", c);
        std::string hex_expr = vespalib::make_string("\"\\x%02x\"", c);
        std::string raw_str = vespalib::make_string("%c", c);
        EXPECT_EQ(raw_str, as_string(*Function::parse(params, hex_expr)));
        if (c != 0 && c != '\"' && c != '\\') {
            EXPECT_EQ(raw_str, as_string(*Function::parse(params, raw_expr)));
        } else {
            EXPECT_TRUE(Function::parse(params, raw_expr)->has_error());
        }
        if (c == '\\') {
            EXPECT_EQ("\"\\\\\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\"') {
            EXPECT_EQ("\"\\\"\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\t') {
            EXPECT_EQ("\"\\t\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\n') {
            EXPECT_EQ("\"\\n\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\r') {
            EXPECT_EQ("\"\\r\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\f') {
            EXPECT_EQ("\"\\f\"", Function::parse(params, hex_expr)->dump());
        } else if ((c >= 32) && (c <= 126)) {
            if (c >= 'a' && c <= 'z' && c != 't' && c != 'n' && c != 'r' && c != 'f') {
                EXPECT_TRUE(Function::parse(params, vespalib::make_string("\"\\%c\"", c))->has_error());
            }
            EXPECT_EQ(raw_expr, Function::parse(params, hex_expr)->dump());
        } else {
            EXPECT_EQ(hex_expr, Function::parse(params, hex_expr)->dump());
        }
    }
}

TEST(FunctionTest, require_that_strings_with_single_quotes_can_be_parsed)
{
    EXPECT_EQ(Function::parse("'foo'")->dump(), "\"foo\"");
    EXPECT_EQ(Function::parse("'fo\\'o'")->dump(), "\"fo'o\"");
}

TEST(FunctionTest, require_that_free_arrays_cannot_be_parsed)
{
    verify_error("[1,2,3]", "[]...[missing value]...[[1,2,3]]");
}

TEST(FunctionTest, require_that_negative_values_can_be_parsed)
{
    EXPECT_EQ("-1", Function::parse(params, "-1")->dump());
    EXPECT_EQ("1", Function::parse(params, "--1")->dump());
    EXPECT_EQ("-1", Function::parse(params, " ( - ( - ( - ( (1) ) ) ) )")->dump());
    EXPECT_EQ("-2.5", Function::parse(params, "-2.5")->dump());
    EXPECT_EQ("-100", Function::parse(params, "-100")->dump());
}

TEST(FunctionTest, require_that_negative_symbols_can_be_parsed)
{
    EXPECT_EQ("(-x)", Function::parse(params, "-x")->dump());
    EXPECT_EQ("(-y)", Function::parse(params, "-y")->dump());
    EXPECT_EQ("(-z)", Function::parse(params, "-z")->dump());
    EXPECT_EQ("(-(-(-x)))", Function::parse(params, "---x")->dump());
}

TEST(FunctionTest, require_that_not_can_be_parsed)
{
    EXPECT_EQ("(!x)", Function::parse(params, "!x")->dump());
    EXPECT_EQ("(!(!x))", Function::parse(params, "!!x")->dump());
    EXPECT_EQ("(!(!(!x)))", Function::parse(params, "!!!x")->dump());
}

TEST(FunctionTest, require_that_not_and_neg_binds_to_next_value)
{
    EXPECT_EQ("((!(!(-(-x))))^z)", Function::parse(params, "!!--x^z")->dump());
    EXPECT_EQ("((-(-(!(!x))))^z)", Function::parse(params, "--!!x^z")->dump());
    EXPECT_EQ("((!(-(-(!x))))^z)", Function::parse(params, "!--!x^z")->dump());
    EXPECT_EQ("((-(!(!(-x))))^z)", Function::parse(params, "-!!-x^z")->dump());
}

TEST(FunctionTest, require_that_parenthesis_resolves_before_not_and_neg)
{
    EXPECT_EQ("(!(x^z))", Function::parse(params, "!(x^z)")->dump());
    EXPECT_EQ("(-(x^z))", Function::parse(params, "-(x^z)")->dump());
}

TEST(FunctionTest, require_that_operators_have_appropriate_binding_order)
{
    verify_operator_binding_order({    { Operator::Order::RIGHT, { "^" } },
                                       { Operator::Order::LEFT,  { "*", "/", "%" } },
                                       { Operator::Order::LEFT,  { "+", "-" } },
                                       { Operator::Order::LEFT,  { "==", "!=", "~=", "<", "<=", ">", ">=" } },
                                       { Operator::Order::LEFT,  { "&&" } },
                                       { Operator::Order::LEFT,  { "||" } } });
}

TEST(FunctionTest, require_that_operators_binding_left_are_calculated_left_to_right)
{
    EXPECT_TRUE(create_op("+")->order() == Operator::Order::LEFT);
    EXPECT_EQ("((x+y)+z)", Function::parse(params, "x+y+z")->dump());
}

TEST(FunctionTest, require_that_operators_binding_right_are_calculated_right_to_left)
{
    EXPECT_TRUE(create_op("^")->order() == Operator::Order::RIGHT);
    EXPECT_EQ("(x^(y^z))", Function::parse(params, "x^y^z")->dump());
}

TEST(FunctionTest, require_that_operators_with_higher_precedence_are_resolved_first)
{
    EXPECT_TRUE(create_op("*")->priority() > create_op("+")->priority());
    EXPECT_EQ("(x+(y*z))", Function::parse(params, "x+y*z")->dump());
    EXPECT_EQ("((x*y)+z)", Function::parse(params, "x*y+z")->dump());
}

TEST(FunctionTest, require_that_multi_level_operator_precedence_resolving_works)
{
    EXPECT_TRUE(create_op("^")->priority() > create_op("*")->priority());
    EXPECT_TRUE(create_op("*")->priority() > create_op("+")->priority());
    EXPECT_EQ("(x+(y*(z^w)))", Function::parse(params, "x+y*z^w")->dump());
    EXPECT_EQ("(x+((y^z)*w))", Function::parse(params, "x+y^z*w")->dump());
    EXPECT_EQ("((x*y)+(z^w))", Function::parse(params, "x*y+z^w")->dump());
    EXPECT_EQ("((x*(y^z))+w)", Function::parse(params, "x*y^z+w")->dump());
    EXPECT_EQ("((x^y)+(z*w))", Function::parse(params, "x^y+z*w")->dump());
    EXPECT_EQ("(((x^y)*z)+w)", Function::parse(params, "x^y*z+w")->dump());
}

TEST(FunctionTest, require_that_expressions_are_combined_when_parenthesis_are_closed)
{
    EXPECT_EQ("((x+(y+z))+w)", Function::parse(params, "x+(y+z)+w")->dump());
}

TEST(FunctionTest, require_that_operators_can_not_bind_out_of_parenthesis)
{
    EXPECT_TRUE(create_op("*")->priority() > create_op("+")->priority());
    EXPECT_EQ("((x+y)*(x+z))", Function::parse(params, "(x+y)*(x+z)")->dump());
}

TEST(FunctionTest, require_that_set_membership_constructs_can_be_parsed)
{
    EXPECT_EQ("(x in [1,2,3])", Function::parse(params, "x in [1,2,3]")->dump());
    EXPECT_EQ("(x in [1,2,3])", Function::parse(params, "x  in  [ 1 , 2 , 3 ] ")->dump());
    EXPECT_EQ("(x in [-1,-2,-3])", Function::parse(params, "x in [-1,-2,-3]")->dump());
    EXPECT_EQ("(x in [-1,-2,-3])", Function::parse(params, "x in [ - 1 , - 2 , - 3 ]")->dump());
    EXPECT_EQ("(x in [1,2,3])", Function::parse(params, "x  in[1,2,3]")->dump());
    EXPECT_EQ("(x in [1,2,3])", Function::parse(params, "(x)in[1,2,3]")->dump());
    EXPECT_EQ("(x in [\"a\",2,\"c\"])", Function::parse(params, "x in [\"a\",2,\"c\"]")->dump());
}

TEST(FunctionTest, require_that_set_membership_entries_must_be_array_of_strings_or_numbers)
{
    verify_error("x in 1", "[x in ]...[expected '[', but got '1']...[1]");
    verify_error("x in ([1])", "[x in ]...[expected '[', but got '(']...[([1])]");
    verify_error("x in [y]", "[x in [y]...[invalid entry for 'in' operator]...[]]");
    verify_error("x in [!1]", "[x in [!1]...[invalid entry for 'in' operator]...[]]");
    verify_error("x in [1+2]", "[x in [1]...[expected ',', but got '+']...[+2]]");
    verify_error("x in [-\"foo\"]", "[x in [-\"foo\"]...[invalid entry for 'in' operator]...[]]");
}

TEST(FunctionTest, require_that_set_membership_binds_to_the_next_value)
{
    EXPECT_EQ("((x in [1,2,3])^2)", Function::parse(params, "x in [1,2,3]^2")->dump());
}

TEST(FunctionTest, require_that_set_membership_binds_to_the_left_with_appropriate_precedence)
{
    EXPECT_EQ("((x<y) in [1,2,3])", Function::parse(params, "x < y in [1,2,3]")->dump());
    EXPECT_EQ("(x&&(y in [1,2,3]))", Function::parse(params, "x && y in [1,2,3]")->dump());
}

TEST(FunctionTest, require_that_function_calls_can_be_parsed)
{
    EXPECT_EQ("min(max(x,y),sqrt(z))", Function::parse(params, "min(max(x,y),sqrt(z))")->dump());
}

TEST(FunctionTest, require_that_if_expressions_can_be_parsed)
{
    EXPECT_EQ("if(x,y,z)", Function::parse(params, "if(x,y,z)")->dump());
    EXPECT_EQ("if(x,y,z)", Function::parse(params, "if (x,y,z)")->dump());
    EXPECT_EQ("if(x,y,z)", Function::parse(params, " if ( x , y , z ) ")->dump());
    EXPECT_EQ("if(((x>1)&&(y<3)),(y+1),(z-1))", Function::parse(params, "if(x>1&&y<3,y+1,z-1)")->dump());
    EXPECT_EQ("if(if(x,y,z),if(x,y,z),if(x,y,z))", Function::parse(params, "if(if(x,y,z),if(x,y,z),if(x,y,z))")->dump());
    EXPECT_EQ("if(x,y,z,0.25)", Function::parse(params, "if(x,y,z,0.25)")->dump());
    EXPECT_EQ("if(x,y,z,0.75)", Function::parse(params, "if(x,y,z,0.75)")->dump());
}

TEST(FunctionTest, require_that_if_probability_can_be_inspected)
{
    auto fun_1 = Function::parse("if(x,y,z,0.25)");
    auto if_1 = as<If>(fun_1->root());
    ASSERT_TRUE(if_1);
    EXPECT_EQ(0.25, if_1->p_true());
    auto fun_2 = Function::parse("if(x,y,z,0.75)");
    auto if_2 = as<If>(fun_2->root());
    ASSERT_TRUE(if_2);
    EXPECT_EQ(0.75, if_2->p_true());
}

TEST(FunctionTest, require_that_symbols_can_be_implicit)
{
    EXPECT_EQ("x", Function::parse("x")->dump());
    EXPECT_EQ("y", Function::parse("y")->dump());
    EXPECT_EQ("z", Function::parse("z")->dump());
}

TEST(FunctionTest, require_that_implicit_parameters_are_picket_up_left_to_right)
{
    auto fun1 = Function::parse("x+y+y");
    auto fun2 = Function::parse("y+y+x");
    EXPECT_EQ("((x+y)+y)", fun1->dump());
    EXPECT_EQ("((y+y)+x)", fun2->dump());
    ASSERT_EQ(2u, fun1->num_params());
    ASSERT_EQ(2u, fun2->num_params());
    EXPECT_EQ("x", fun1->param_name(0));
    EXPECT_EQ("x", fun2->param_name(1));
    EXPECT_EQ("y", fun1->param_name(1));
    EXPECT_EQ("y", fun2->param_name(0));
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_leaf_nodes_have_no_children)
{
    EXPECT_TRUE(Function::parse("123")->root().is_leaf());
    EXPECT_TRUE(Function::parse("x")->root().is_leaf());
    EXPECT_TRUE(Function::parse("\"abc\"")->root().is_leaf());
    EXPECT_EQ(0u, Function::parse("123")->root().num_children());
    EXPECT_EQ(0u, Function::parse("x")->root().num_children());
    EXPECT_EQ(0u, Function::parse("\"abc\"")->root().num_children());
}

TEST(FunctionTest, require_that_Neg_child_can_be_accessed)
{
    auto f = Function::parse("-x");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQ(1u, root.num_children());
    EXPECT_TRUE(root.get_child(0).is_param());
}

TEST(FunctionTest, require_that_Not_child_can_be_accessed)
{
    auto f = Function::parse("!1");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQ(1u, root.num_children());
    EXPECT_EQ(1.0, root.get_child(0).get_const_double_value());
}

TEST(FunctionTest, require_that_If_children_can_be_accessed)
{
    auto f = Function::parse("if(1,2,3)");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQ(3u, root.num_children());
    EXPECT_EQ(1.0, root.get_child(0).get_const_double_value());
    EXPECT_EQ(2.0, root.get_child(1).get_const_double_value());
    EXPECT_EQ(3.0, root.get_child(2).get_const_double_value());
}

TEST(FunctionTest, require_that_Operator_children_can_be_accessed)
{
    auto f = Function::parse("1+2");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQ(2u, root.num_children());
    EXPECT_EQ(1.0, root.get_child(0).get_const_double_value());
    EXPECT_EQ(2.0, root.get_child(1).get_const_double_value());
}

TEST(FunctionTest, require_that_Call_children_can_be_accessed)
{
    auto f = Function::parse("max(1,2)");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQ(2u, root.num_children());
    EXPECT_EQ(1.0, root.get_child(0).get_const_double_value());
    EXPECT_EQ(2.0, root.get_child(1).get_const_double_value());
}

struct MyNodeHandler : public NodeHandler {
    std::vector<nodes::Node_UP> nodes;
    void handle(nodes::Node_UP node) override {
        if (node.get() != nullptr) {
            nodes.push_back(std::move(node));
        }
    }
};

size_t detach_from_root(const std::string &expr) {
    MyNodeHandler handler;
    auto function = Function::parse(expr);
    nodes::Node &mutable_root = const_cast<nodes::Node&>(function->root());
    mutable_root.detach_children(handler);
    return handler.nodes.size();
}

TEST(FunctionTest, require_that_children_can_be_detached)
{
    EXPECT_EQ(0u, detach_from_root("1"));
    EXPECT_EQ(0u, detach_from_root("a"));
    EXPECT_EQ(1u, detach_from_root("-a"));
    EXPECT_EQ(1u, detach_from_root("!a"));
    EXPECT_EQ(3u, detach_from_root("if(1,2,3)"));
    EXPECT_EQ(1u, detach_from_root("a in [1,2,3,4,5]"));
    EXPECT_EQ(2u, detach_from_root("a+b"));
    EXPECT_EQ(1u, detach_from_root("isNan(a)"));
    EXPECT_EQ(2u, detach_from_root("max(a,b)"));
}

//-----------------------------------------------------------------------------

struct MyTraverser : public NodeTraverser {
    size_t open_true_cnt;
    std::vector<std::pair<bool, const nodes::Node &> > history;
    explicit MyTraverser(size_t open_true_cnt_in)
        : open_true_cnt(open_true_cnt_in), history() {}
    ~MyTraverser() override;
    bool open(const nodes::Node &node) override {
        history.emplace_back(true, node);
        if (open_true_cnt == 0) {
            return false;
        }
        --open_true_cnt;
        return true;
    }
    void close(const nodes::Node &node) override {
        history.emplace_back(false, node);
    }
    void verify(const nodes::Node &node, size_t &offset, size_t &open_cnt) {
        ASSERT_TRUE(history.size() > offset);
        EXPECT_TRUE(history[offset].first);
        EXPECT_EQ(&node, &history[offset].second);
        ++offset;
        if (open_cnt == 0) {
            return;
        }
        --open_cnt;
        for (size_t i = 0; i < node.num_children(); ++i) {
            verify(node.get_child(i), offset, open_cnt);
        }
        ASSERT_TRUE(history.size() > offset);
        EXPECT_TRUE(!history[offset].first);
        EXPECT_EQ(&node, &history[offset].second);
        ++offset;
    }
};

MyTraverser::~MyTraverser() = default;

size_t verify_traversal(size_t open_true_cnt, const std::string &expression) {
    auto function = Function::parse(expression);
    EXPECT_TRUE(!function->has_error()) << "--> " << function->get_error();
    MyTraverser traverser(open_true_cnt);
    function->root().traverse(traverser);
    size_t offset = 0;
    size_t open_cnt = open_true_cnt;
    traverser.verify(function->root(), offset, open_cnt);
    EXPECT_EQ(offset, traverser.history.size());
    return offset;
}

void verify_expression_traversal(const std::string &expression) {
    SCOPED_TRACE(expression);
    for (size_t open_cnt = 0; true; ++open_cnt) {
        size_t num_callbacks = verify_traversal(open_cnt, expression);
        if (num_callbacks == (open_cnt * 2)) { // graph is now fully expanded
            EXPECT_EQ(open_cnt * 2, verify_traversal(open_cnt + 1, expression));
            return;
        }
    }
}

TEST(FunctionTest, require_that_traversal_works_as_expected)
{
    verify_expression_traversal("1");
    verify_expression_traversal("1+2");
    verify_expression_traversal("1+2*3-4/5");
    verify_expression_traversal("if(x,1+2*3,if(a,b,c)/5)");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_node_types_can_be_checked)
{
    EXPECT_TRUE(nodes::check_type<nodes::Add>(Function::parse("1+2")->root()));
    EXPECT_TRUE(!nodes::check_type<nodes::Add>(Function::parse("1-2")->root()));
    EXPECT_TRUE(!nodes::check_type<nodes::Add>(Function::parse("1*2")->root()));
    EXPECT_TRUE(!nodes::check_type<nodes::Add>(Function::parse("1/2")->root()));
    EXPECT_TRUE((nodes::check_type<nodes::Add, nodes::Sub, nodes::Mul>(Function::parse("1+2")->root())));
    EXPECT_TRUE((nodes::check_type<nodes::Add, nodes::Sub, nodes::Mul>(Function::parse("1-2")->root())));
    EXPECT_TRUE((nodes::check_type<nodes::Add, nodes::Sub, nodes::Mul>(Function::parse("1*2")->root())));
    EXPECT_TRUE((!nodes::check_type<nodes::Add, nodes::Sub, nodes::Mul>(Function::parse("1/2")->root())));
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_parameter_is_param_but_not_const)
{
    EXPECT_TRUE(Function::parse("x")->root().is_param());
    EXPECT_TRUE(!Function::parse("x")->root().is_const_double());
}

TEST(FunctionTest, require_that_inverted_parameter_is_not_param)
{
    EXPECT_TRUE(!Function::parse("-x")->root().is_param());
}

TEST(FunctionTest, require_that_number_is_const_but_not_param)
{
    EXPECT_TRUE(Function::parse("123")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("123")->root().is_param());
}

TEST(FunctionTest, require_that_string_is_const)
{
    EXPECT_TRUE(Function::parse("\"x\"")->root().is_const_double());
}

TEST(FunctionTest, require_that_neg_is_const_if_sub_expression_is_const)
{
    EXPECT_TRUE(Function::parse("-123")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("-x")->root().is_const_double());
}

TEST(FunctionTest, require_that_not_is_const_if_sub_expression_is_const)
{
    EXPECT_TRUE(Function::parse("!1")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("!x")->root().is_const_double());
}

TEST(FunctionTest, require_that_operators_are_cost_if_both_children_are_const)
{
    EXPECT_TRUE(!Function::parse("x+y")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("1+y")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("x+2")->root().is_const_double());
    EXPECT_TRUE(Function::parse("1+2")->root().is_const_double());
}

TEST(FunctionTest, require_that_set_membership_is_never_tagged_as_const_NB_avoids_jit_recursion)
{
    EXPECT_TRUE(!Function::parse("x in [x,y,z]")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("1 in [x,y,z]")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("1 in [1,y,z]")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("1 in [1,2,3]")->root().is_const_double());
}

TEST(FunctionTest, require_that_calls_are_cost_if_all_parameters_are_const)
{
    EXPECT_TRUE(!Function::parse("max(x,y)")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("max(1,y)")->root().is_const_double());
    EXPECT_TRUE(!Function::parse("max(x,2)")->root().is_const_double());
    EXPECT_TRUE(Function::parse("max(1,2)")->root().is_const_double());
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_feature_less_than_constant_is_tree_if_children_are_trees_or_constants)
{
    EXPECT_TRUE(Function::parse("if (foo < 2, 3, 4)")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo < 2, if(bar < 3, 4, 5), 6)")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo < 2, if(bar < 3, 4, 5), if(baz < 6, 7, 8))")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo < 2, 3, if(baz < 4, 5, 6))")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo < max(1,2), 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (2 < foo, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (foo < bar, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (1 < 2, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (foo <= 2, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (foo == 2, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (foo > 2, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (foo >= 2, 3, 4)")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (foo ~= 2, 3, 4)")->root().is_tree());
}

TEST(FunctionTest, require_that_feature_in_set_of_constants_is_tree_if_children_are_trees_or_constants)
{
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], 3, 4)")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], if(bar < 3, 4, 5), 6)")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], if(bar < 3, 4, 5), if(baz < 6, 7, 8))")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], 3, if(baz < 4, 5, 6))")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], min(1,3), max(1,4))")->root().is_tree());
    EXPECT_TRUE(!Function::parse("if (1 in [1, 2], 3, 4)")->root().is_tree());
}

TEST(FunctionTest, require_that_sums_of_trees_and_forests_are_forests)
{
    EXPECT_TRUE(Function::parse("if(foo<1,2,3) + if(bar<4,5,6)")->root().is_forest());
    EXPECT_TRUE(Function::parse("if(foo<1,2,3) + if(bar<4,5,6) + if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) + 10")->root().is_forest());
    EXPECT_TRUE(!Function::parse("10 + if(bar<4,5,6)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) - if(bar<4,5,6)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) * if(bar<4,5,6)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) / if(bar<4,5,6)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) ^ if(bar<4,5,6)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) - if(bar<4,5,6) + if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) * if(bar<4,5,6) + if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) / if(bar<4,5,6) + if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) ^ if(bar<4,5,6) + if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) + if(bar<4,5,6) - if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) + if(bar<4,5,6) * if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) + if(bar<4,5,6) / if(bar<7,8,9)")->root().is_forest());
    EXPECT_TRUE(!Function::parse("if(foo<1,2,3) + if(bar<4,5,6) ^ if(bar<7,8,9)")->root().is_forest());
}

//-----------------------------------------------------------------------------

struct UnWrapped {
    std::string wrapper;
    std::string body;
    std::string error;
    ~UnWrapped();
};


UnWrapped::~UnWrapped() {}

UnWrapped unwrap(const std::string &str) {
    UnWrapped result;
    bool ok = Function::unwrap(str, result.wrapper, result.body, result.error);
    EXPECT_EQ(ok, result.error.empty());
    return result;
}

TEST(FunctionTest, require_that_unwrapping_works)
{
    EXPECT_EQ("max", unwrap("max(x+y)").wrapper);
    EXPECT_EQ("max", unwrap("  max(x+y)").wrapper);
    EXPECT_EQ("max", unwrap("  max  (x+y)").wrapper);
    EXPECT_EQ("x+y", unwrap("max(x+y)").body);
    EXPECT_EQ("x+y", unwrap("max(x+y)  ").body);
    EXPECT_EQ("max", unwrap("max()").wrapper);
    EXPECT_EQ("", unwrap("max()").body);
    EXPECT_EQ("", unwrap("max()").error);
    EXPECT_EQ("could not extract wrapper name", unwrap("").error);
    EXPECT_EQ("could not extract wrapper name", unwrap("(x+y)").error);
    EXPECT_EQ("could not extract wrapper name", unwrap("  (x+y)").error);
    EXPECT_EQ("could not match opening '('", unwrap("max").error);
    EXPECT_EQ("could not match opening '('", unwrap("max)").error);
    EXPECT_EQ("could not match opening '('", unwrap("max5(x+y)").error);
    EXPECT_EQ("could not match opening '('", unwrap("max)x+y(").error);
    EXPECT_EQ("could not match closing ')'", unwrap("max(x+y").error);
    EXPECT_EQ("could not match closing ')'", unwrap("max(x+y)x").error);
    EXPECT_EQ("could not match closing ')'", unwrap("max(").error);
}

//-----------------------------------------------------------------------------

struct MySymbolExtractor : SymbolExtractor {
    std::vector<char> extra;
    mutable size_t invoke_count;
    bool is_extra(char c) const {
        for (char extra_char: extra) {
            if (c == extra_char) {
                return true;
            }
        }
        return false;
    }
    MySymbolExtractor() : extra(), invoke_count() {}
    explicit MySymbolExtractor(std::initializer_list<char> extra_in) : extra(extra_in), invoke_count() {}

    void extract_symbol(const char *pos_in, const char *end_in,
                        const char *&pos_out, std::string &symbol_out) const override
    {
        ++invoke_count;
        for (; pos_in < end_in; ++pos_in) {
            char c = *pos_in;
            if ((c >= 'a' && c <= 'z') || is_extra(c)) {
                symbol_out.push_back(c);
            } else {
                break;
            }
        }
        pos_out = pos_in;
    }
};

TEST(FunctionTest, require_that_custom_symbol_extractor_may_be_used)
{
    EXPECT_EQ("[x+]...[missing value]...[*y]", Function::parse(params, "x+*y")->dump());
    EXPECT_EQ("[x+]...[missing value]...[*y]", Function::parse(params, "x+*y", MySymbolExtractor())->dump());
    EXPECT_EQ("[x+]...[unknown symbol: 'x+']...[*y]", Function::parse(params, "x+*y", MySymbolExtractor({'+'}))->dump());
    EXPECT_EQ("[x+*y]...[unknown symbol: 'x+*y']...[]", Function::parse(params, "x+*y", MySymbolExtractor({'+', '*'}))->dump());
}

TEST(FunctionTest, require_that_unknown_function_works_as_expected_with_custom_symbol_extractor)
{
    EXPECT_EQ("[bogus(]...[unknown function: 'bogus']...[x)+y]", Function::parse(params, "bogus(x)+y")->dump());
    EXPECT_EQ("[bogus]...[unknown symbol: 'bogus']...[(x)+y]", Function::parse(params, "bogus(x)+y", MySymbolExtractor())->dump());
    EXPECT_EQ("[bogus(x)]...[unknown symbol: 'bogus(x)']...[+y]", Function::parse(params, "bogus(x)+y", MySymbolExtractor({'(', ')'}))->dump());
}

TEST(FunctionTest, require_that_unknown_function_that_is_valid_parameter_works_as_expected_with_custom_symbol_extractor)
{
    EXPECT_EQ("[z(]...[unknown function: 'z']...[x)+y]", Function::parse(params, "z(x)+y")->dump());
    EXPECT_EQ("[z]...[invalid operator: '(']...[(x)+y]", Function::parse(params, "z(x)+y", MySymbolExtractor())->dump());
    EXPECT_EQ("[z(x)]...[unknown symbol: 'z(x)']...[+y]", Function::parse(params, "z(x)+y", MySymbolExtractor({'(', ')'}))->dump());
}

TEST(FunctionTest, require_that_custom_symbol_extractor_is_not_invoked_for_known_function_call)
{
    MySymbolExtractor extractor;
    EXPECT_EQ(extractor.invoke_count, 0u);
    EXPECT_EQ("[bogus]...[unknown symbol: 'bogus']...[(1,2)]", Function::parse(params, "bogus(1,2)", extractor)->dump());
    EXPECT_EQ(extractor.invoke_count, 1u);
    EXPECT_EQ("max(1,2)", Function::parse(params, "max(1,2)", extractor)->dump());
    EXPECT_EQ(extractor.invoke_count, 1u);
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_valid_function_does_not_report_parse_error)
{
    auto function = Function::parse(params, "x + y");
    EXPECT_TRUE(!function->has_error());
    EXPECT_EQ("", function->get_error());
}

TEST(FunctionTest, require_that_an_invalid_function_with_explicit_paramers_retain_its_parameters)
{
    auto function = Function::parse({"x", "y"}, "x & y");
    EXPECT_TRUE(function->has_error());
    ASSERT_EQ(2u, function->num_params());
    ASSERT_EQ("x", function->param_name(0));
    ASSERT_EQ("y", function->param_name(1));
}

TEST(FunctionTest, require_that_an_invalid_function_with_implicit_paramers_has_no_parameters)
{
    auto function = Function::parse("x & y");
    EXPECT_TRUE(function->has_error());
    EXPECT_EQ(0u, function->num_params());
}

TEST(FunctionTest, require_that_unknown_operator_gives_parse_error)
{
    verify_error("x&y", "[x]...[invalid operator: '&']...[&y]");
}

TEST(FunctionTest, require_that_unknown_symbol_gives_parse_error)
{
    verify_error("x+a", "[x+a]...[unknown symbol: 'a']...[]");
}

TEST(FunctionTest, require_that_missing_value_gives_parse_error)
{
    verify_error("x+", "[x+]...[missing value]...[]");
    verify_error("x++y", "[x+]...[missing value]...[+y]");
    verify_error("x+++y", "[x+]...[missing value]...[++y]");
    verify_error("x+(y+)+z", "[x+(y+]...[missing value]...[)+z]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_operations_can_be_nested)
{
    EXPECT_EQ("reduce(reduce(reduce(a,sum),sum),sum,dim)",
                 Function::parse("reduce(reduce(reduce(a,sum),sum),sum,dim)")->dump());
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_map_can_be_parsed)
{
    EXPECT_EQ("map(a,f(x)(x+1))", Function::parse("map(a,f(x)(x+1))")->dump());
    EXPECT_EQ("map(a,f(x)(x+1))", Function::parse(" map ( a , f ( x ) ( x + 1 ) ) ")->dump());
}

TEST(FunctionTest, require_that_tensor_join_can_be_parsed)
{
    EXPECT_EQ("join(a,b,f(x,y)(x+y))", Function::parse("join(a,b,f(x,y)(x+y))")->dump());
    EXPECT_EQ("join(a,b,f(x,y)(x+y))", Function::parse(" join ( a , b , f ( x , y ) ( x + y ) ) ")->dump());
}

TEST(FunctionTest, require_that_parenthesis_are_added_around_lambda_expression_when_needed)
{
    EXPECT_EQ("f(x)(sin(x))", Function::parse("sin(x)")->dump_as_lambda());
}

TEST(FunctionTest, require_that_parse_error_inside_a_lambda_fails_the_enclosing_expression)
{
    verify_error("map(x,f(a)(b))", "[map(x,f(a)(b]...[unknown symbol: 'b']...[))]");
}

TEST(FunctionTest, require_that_outer_parameters_are_hidden_within_a_lambda)
{
    verify_error("map(x,f(a)(y))", "[map(x,f(a)(y]...[unknown symbol: 'y']...[))]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_reduce_can_be_parsed)
{
    EXPECT_EQ("reduce(x,sum,a,b)", Function::parse({"x"}, "reduce(x,sum,a,b)")->dump());
    EXPECT_EQ("reduce(x,sum,a,b,c)", Function::parse({"x"}, "reduce(x,sum,a,b,c)")->dump());
    EXPECT_EQ("reduce(x,sum,a,b,c)", Function::parse({"x"}, " reduce ( x , sum , a , b , c ) ")->dump());
    EXPECT_EQ("reduce(x,sum)", Function::parse({"x"}, "reduce(x,sum)")->dump());
    EXPECT_EQ("reduce(x,avg)", Function::parse({"x"}, "reduce(x,avg)")->dump());
    EXPECT_EQ("reduce(x,avg)", Function::parse({"x"}, "reduce( x , avg )")->dump());
    EXPECT_EQ("reduce(x,count)", Function::parse({"x"}, "reduce(x,count)")->dump());
    EXPECT_EQ("reduce(x,prod)", Function::parse({"x"}, "reduce(x,prod)")->dump());
    EXPECT_EQ("reduce(x,min)", Function::parse({"x"}, "reduce(x,min)")->dump());
    EXPECT_EQ("reduce(x,max)", Function::parse({"x"}, "reduce(x,max)")->dump());
}

TEST(FunctionTest, require_that_tensor_reduce_with_unknown_aggregator_fails)
{
    verify_error("reduce(x,bogus)", "[reduce(x,bogus]...[unknown aggregator: 'bogus']...[)]");
}

TEST(FunctionTest, require_that_tensor_reduce_with_duplicate_dimensions_fails)
{
    verify_error("reduce(x,sum,a,a)", "[reduce(x,sum,a,a]...[duplicate identifiers]...[)]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_rename_can_be_parsed)
{
    EXPECT_EQ("rename(x,a,b)", Function::parse({"x"}, "rename(x,a,b)")->dump());
    EXPECT_EQ("rename(x,a,b)", Function::parse({"x"}, "rename(x,(a),(b))")->dump());
    EXPECT_EQ("rename(x,a,b)", Function::parse({"x"}, "rename(x,a,(b))")->dump());
    EXPECT_EQ("rename(x,a,b)", Function::parse({"x"}, "rename(x,(a),b)")->dump());
    EXPECT_EQ("rename(x,(a,b),(b,a))", Function::parse({"x"}, "rename(x,(a,b),(b,a))")->dump());
    EXPECT_EQ("rename(x,a,b)", Function::parse({"x"}, "rename( x , a , b )")->dump());
    EXPECT_EQ("rename(x,a,b)", Function::parse({"x"}, "rename( x , ( a ) , ( b ) )")->dump());
    EXPECT_EQ("rename(x,(a,b),(b,a))", Function::parse({"x"}, "rename( x , ( a , b ) , ( b , a ) )")->dump());
}

TEST(FunctionTest, require_that_tensor_rename_dimension_lists_cannot_be_empty)
{
    verify_error("rename(x,,b)", "[rename(x,]...[missing identifier]...[,b)]");
    verify_error("rename(x,a,)", "[rename(x,a,]...[missing identifier]...[)]");
    verify_error("rename(x,(),b)", "[rename(x,()]...[missing identifiers]...[,b)]");
    verify_error("rename(x,a,())", "[rename(x,a,()]...[missing identifiers]...[)]");
}

TEST(FunctionTest, require_that_tensor_rename_dimension_lists_cannot_contain_duplicates)
{
    verify_error("rename(x,(a,a),(b,a))", "[rename(x,(a,a)]...[duplicate identifiers]...[,(b,a))]");
    verify_error("rename(x,(a,b),(b,b))", "[rename(x,(a,b),(b,b)]...[duplicate identifiers]...[)]");
}

TEST(FunctionTest, require_that_tensor_rename_dimension_lists_must_have_equal_size)
{
    verify_error("rename(x,(a,b),(b))", "[rename(x,(a,b),(b)]...[dimension list size mismatch]...[)]");
    verify_error("rename(x,(a),(b,a))", "[rename(x,(a),(b,a)]...[dimension list size mismatch]...[)]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_lambda_can_be_parsed)
{
    EXPECT_EQ("tensor(x[3])(x)", Function::parse({}, "tensor(x[3])(x)")->dump());
    EXPECT_EQ("tensor(x[2],y[2])(x==y)",
                 Function::parse({}, " tensor ( x [ 2 ] , y [ 2 ] ) ( x == y ) ")->dump());
}

TEST(FunctionTest, require_that_tensor_lambda_requires_appropriate_tensor_type)
{
    verify_error("tensor(x[10],y[])(x==y)", "[tensor(x[10],y[])]...[invalid tensor type]...[(x==y)]");
    verify_error("tensor(x[10],y{})(x==y)", "[tensor(x[10],y{})]...[invalid tensor type]...[(x==y)]");
    verify_error("tensor()(x==y)", "[tensor()]...[invalid tensor type]...[(x==y)]");
}

TEST(FunctionTest, require_that_tensor_lambda_can_use_non_dimension_symbols)
{
    EXPECT_EQ("tensor(x[2])(x==a)",
                 Function::parse({"a"}, "tensor(x[2])(x==a)")->dump());
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_verbose_tensor_create_can_be_parsed)
{
    auto dense = Function::parse("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}");
    auto sparse1 = Function::parse("tensor(x{}):{{x:a}:1,{x:b}:2,{x:c}:3}");
    auto sparse2 = Function::parse("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}");
    auto sparse3 = Function::parse("tensor(x{}):{{x:'a'}:1,{x:'b'}:2,{x:'c'}:3}");
    auto mixed1 = Function::parse("tensor(x{},y[2]):{{x:a,y:0}:1,{x:a,y:1}:2}");
    auto mixed2 = Function::parse("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}");
    EXPECT_EQ("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}", dense->dump());
    EXPECT_EQ("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse1->dump());
    EXPECT_EQ("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse2->dump());
    EXPECT_EQ("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse3->dump());
    EXPECT_EQ("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed1->dump());
    EXPECT_EQ("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed2->dump());
}

TEST(FunctionTest, require_that_verbose_tensor_create_can_contain_expressions)
{
    auto fun = Function::parse("tensor(x[2]):{{x:0}:1,{x:1}:2+a}");
    EXPECT_EQ("tensor(x[2]):{{x:0}:1,{x:1}:(2+a)}", fun->dump());
    ASSERT_EQ(fun->num_params(), 1u);
    EXPECT_EQ(fun->param_name(0), "a");
}

TEST(FunctionTest, require_that_verbose_tensor_create_handles_spaces_and_reordering_of_various_elements)
{
    auto fun = Function::parse(" tensor ( y [ 2 ] , x [ 2 ] ) : { { x : 0 , y : 1 } : 2 , "
                               "{ y : 0 , x : 0 } : 1 , { y : 0 , x : 1 } : 3 , { x : 1 , y : 1 } : 4 } ");
    EXPECT_EQ("tensor(x[2],y[2]):{{x:0,y:0}:1,{x:0,y:1}:2,{x:1,y:0}:3,{x:1,y:1}:4}", fun->dump());
}

TEST(FunctionTest, require_that_verbose_tensor_create_detects_invalid_tensor_type)
{
    verify_error("tensor(x[,y}):{{ignored}}",
                 "[tensor(x[,y})]...[invalid tensor type]...[:{{ignored}}]");
}

TEST(FunctionTest, require_that_verbose_tensor_create_detects_incomplete_addresses)
{
    verify_error("tensor(x[1],y[1]):{{x:0}:1}",
                 "[tensor(x[1],y[1]):{{x:0}]...[incomplete address: '{x:0}']...[:1}]");
}

TEST(FunctionTest, require_that_verbose_tensor_create_detects_invalid_dimension_names)
{
    verify_error("tensor(x[1]):{{y:0}:1}",
                 "[tensor(x[1]):{{y]...[invalid dimension name: 'y']...[:0}:1}]");
}

TEST(FunctionTest, require_that_verbose_tensor_create_detects_out_of_bounds_indexes_for_indexed_dimensions)
{
    verify_error("tensor(x[1]):{{x:1}:1}",
                 "[tensor(x[1]):{{x:1]...[dimension index too large: 1]...[}:1}]");
}

TEST(FunctionTest, require_that_verbose_tensor_create_detects_non_numeric_indexes_for_indexed_dimensions)
{
    verify_error("tensor(x[1]):{{x:foo}:1}",
                 "[tensor(x[1]):{{x:]...[expected number]...[foo}:1}]");
}

TEST(FunctionTest, require_that_verbose_tensor_create_indexes_cannot_be_quoted)
{
    verify_error("tensor(x[1]):{{x:\"1\"}:1}",
                 "[tensor(x[1]):{{x:]...[expected number]...[\"1\"}:1}]");
    verify_error("tensor(x[1]):{{x:'1'}:1}",
                 "[tensor(x[1]):{{x:]...[expected number]...['1'}:1}]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_convenient_tensor_create_can_be_parsed)
{
    auto dense = Function::parse("tensor(x[3]):[1,2,3]");
    auto sparse1 = Function::parse("tensor(x{}):{a:1,b:2,c:3}");
    auto sparse2 = Function::parse("tensor(x{}):{\"a\":1,\"b\":2,\"c\":3}");
    auto sparse3 = Function::parse("tensor(x{}):{'a':1,'b':2,'c':3}");
    auto mixed1 = Function::parse("tensor(x{},y[2]):{a:[1,2]}");
    auto mixed2 = Function::parse("tensor(x{},y[2]):{\"a\":[1,2]}");
    EXPECT_EQ("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}", dense->dump());
    EXPECT_EQ("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse1->dump());
    EXPECT_EQ("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse2->dump());
    EXPECT_EQ("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse3->dump());
    EXPECT_EQ("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed1->dump());
    EXPECT_EQ("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed2->dump());
}

TEST(FunctionTest, require_that_convenient_tensor_create_can_contain_expressions)
{
    auto fun = Function::parse("tensor(x[2]):[1,2+a]");
    EXPECT_EQ("tensor(x[2]):{{x:0}:1,{x:1}:(2+a)}", fun->dump());
    ASSERT_EQ(fun->num_params(), 1u);
    EXPECT_EQ(fun->param_name(0), "a");
}

TEST(FunctionTest, require_that_convenient_tensor_create_handles_dimension_order)
{
    auto mixed = Function::parse("tensor(y{},x[2]):{a:[1,2]}");
    EXPECT_EQ("tensor(x[2],y{}):{{x:0,y:\"a\"}:1,{x:1,y:\"a\"}:2}", mixed->dump());
}

TEST(FunctionTest, require_that_convenient_tensor_create_can_be_highly_nested)
{
    std::string expect("tensor(a{},b{},c[1],d[1]):{{a:\"x\",b:\"y\",c:0,d:0}:5}");
    auto nested1 = Function::parse("tensor(a{},b{},c[1],d[1]):{x:{y:[[5]]}}");
    auto nested2 = Function::parse("tensor(c[1],d[1],a{},b{}):[[{x:{y:5}}]]");
    auto nested3 = Function::parse("tensor(a{},c[1],b{},d[1]): { x : [ { y : [ 5 ] } ] } ");
    EXPECT_EQ(expect, nested1->dump());
    EXPECT_EQ(expect, nested2->dump());
    EXPECT_EQ(expect, nested3->dump());
}

TEST(FunctionTest, require_that_convenient_tensor_create_can_have_multiple_values_on_multiple_levels)
{
    std::string expect("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2,{x:\"b\",y:0}:3,{x:\"b\",y:1}:4}");
    auto fun1 = Function::parse("tensor(x{},y[2]):{a:[1,2],b:[3,4]}");
    auto fun2 = Function::parse("tensor(y[2],x{}):[{a:1,b:3},{a:2,b:4}]");
    auto fun3 = Function::parse("tensor(x{},y[2]): { a : [ 1 , 2 ] , b : [ 3 , 4 ] } ");
    auto fun4 = Function::parse("tensor(y[2],x{}): [ { a : 1 , b : 3 } , { a : 2 , b : 4 } ] ");
    EXPECT_EQ(expect, fun1->dump());
    EXPECT_EQ(expect, fun2->dump());
    EXPECT_EQ(expect, fun3->dump());
    EXPECT_EQ(expect, fun4->dump());
}

TEST(FunctionTest, require_that_convenient_tensor_create_allows_under_specified_tensors)
{
    auto fun = Function::parse("tensor(x[2],y[2]):[[],[5]]");
    EXPECT_EQ("tensor(x[2],y[2]):{{x:1,y:0}:5}", fun->dump());
}

TEST(FunctionTest, require_that_convenient_tensor_create_detects_invalid_tensor_type)
{
    verify_error("tensor(x[,y}):ignored",
                 "[tensor(x[,y})]...[invalid tensor type]...[:ignored]");
}

TEST(FunctionTest, require_that_convenient_tensor_create_detects_too_large_indexed_dimensions)
{
    verify_error("tensor(x[1]):[1,2]",
                 "[tensor(x[1]):[1,]...[dimension too large: 'x']...[2]]");
}

TEST(FunctionTest, require_that_convenient_tensor_create_detects_under_specified_cells)
{
    verify_error("tensor(x[1],y[1]):[1]",
                 "[tensor(x[1],y[1]):[]...[expected '[', but got '1']...[1]]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_peek_can_be_parsed)
{
    verify_parse("t{x:\"1\",y:\"foo\"}", "f(t)(t{x:\"1\",y:\"foo\"})");
    verify_parse("t{x:'1',y:'foo'}", "f(t)(t{x:\"1\",y:\"foo\"})");
    verify_parse("t{x:1,y:foo}", "f(t)(t{x:\"1\",y:\"foo\"})");
}

TEST(FunctionTest, require_that_tensor_peek_can_contain_expressions)
{
    verify_parse("t{x:(1+2),y:1+2}", "f(t)(t{x:(1+2),y:\"1+2\"})");
    verify_parse("t{x:(foo),y:foo}", "f(t,foo)(t{x:(foo),y:\"foo\"})");
    verify_parse("t{x:(foo+2),y:foo+2}", "f(t,foo)(t{x:(foo+2),y:\"foo+2\"})");
}

TEST(FunctionTest, require_that_trivial_tensor_peek_number_expressions_are_converted_to_verbatim_labels)
{
    verify_parse("t{x:(5.7)}", "f(t)(t{x:\"5\"})");
    verify_parse("t{x:(5.3)}", "f(t)(t{x:\"5\"})");
    verify_parse("t{x:(-5.7)}", "f(t)(t{x:\"-5\"})");
    verify_parse("t{x:(-5.3)}", "f(t)(t{x:\"-5\"})");
}

TEST(FunctionTest, require_that_tensor_peek_can_contain_extra_whitespace)
{
    verify_parse(" t { x : ( 1 + bar ) , y : ( foo + 2 ) } ",
                 "f(t,bar,foo)(t{x:(1+bar),y:(foo+2)})");
    verify_parse(" t { x : \"1 + bar\" , y : \"foo + 2\" } ",
                 "f(t)(t{x:\"1 + bar\",y:\"foo + 2\"})");
}

TEST(FunctionTest, require_that_empty_tensor_peek_is_not_allowed)
{
    verify_error("x{}", "[x{}]...[empty peek spec]...[]");
}

TEST(FunctionTest, require_that_tensor_peek_empty_label_is_not_allowed)
{
    verify_error("x{a:}", "[x{a:]...[missing label]...[}]");
    verify_error("x{a:\"\"}", "[x{a:\"\"]...[missing label]...[}]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_nested_tensor_lambda_using_tensor_peek_can_be_parsed)
{
    std::string expect("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})");
    EXPECT_EQ(Function::parse(expect)->dump(), expect);
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_concat_can_be_parsed)
{
    EXPECT_EQ("concat(a,b,d)", Function::parse({"a", "b"}, "concat(a,b,d)")->dump());
    EXPECT_EQ("concat(a,b,d)", Function::parse({"a", "b"}, " concat ( a , b , d ) ")->dump());
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_cell_cast_can_be_parsed)
{
    EXPECT_EQ("cell_cast(a,float)", Function::parse({"a"}, "cell_cast(a,float)")->dump());
    EXPECT_EQ("cell_cast(a,double)", Function::parse({"a"}, " cell_cast ( a , double ) ")->dump());
}

TEST(FunctionTest, require_that_tensor_cell_cast_must_have_valid_cell_type)
{
    verify_error("cell_cast(x,int7)", "[cell_cast(x,int7]...[unknown cell type: 'int7']...[)]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_cell_order_can_be_parsed)
{
    EXPECT_EQ("cell_order(a,min)", Function::parse({"a"}, "cell_order(a,min)")->dump());
    EXPECT_EQ("cell_order(a,max)", Function::parse({"a"}, " cell_order ( a , max ) ")->dump());
}

TEST(FunctionTest, require_that_tensor_cell_order_must_have_valid_order)
{
    verify_error("cell_order(x,avg)", "[cell_order(x,avg]...[unknown cell order: 'avg']...[)]");
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_tensor_filter_subspaces_can_be_parsed)
{
    EXPECT_EQ("filter_subspaces(a,f(x)(x))", Function::parse({"a"}, "filter_subspaces(a,f(x)(x))")->dump());
    EXPECT_EQ("filter_subspaces(a,f(x)(x))", Function::parse({"a"}, " filter_subspaces ( a , f ( x ) ( x ) ) ")->dump());
}

TEST(FunctionTest, require_that_tensor_filter_subspaces_lambda_is_free)
{
    verify_error("filter_subspaces(x,f(a)(y))", "[filter_subspaces(x,f(a)(y]...[unknown symbol: 'y']...[))]");
}

//-----------------------------------------------------------------------------

struct CheckExpressions : test::EvalSpec::EvalTest {
    bool failed = false;
    size_t seen_cnt = 0;
    ~CheckExpressions() override;
    virtual void next_expression(const std::vector<std::string> &param_names,
                                 const std::string &expression) override
    {
        auto function = Function::parse(param_names, expression);
        if (function->has_error()) {
            failed = true;
            fprintf(stderr, "parse error: %s\n", function->get_error().c_str());
        }
        ++seen_cnt;
    }
    virtual void handle_case(const std::vector<std::string> &,
                             const std::vector<double> &,
                             const std::string &,
                             double) override {}
};

CheckExpressions::~CheckExpressions() = default;

TEST(FunctionTest, require_that_all_conformance_test_expressions_can_be_parsed)
{
    CheckExpressions f1;
    test::EvalSpec f2;
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_TRUE(!f1.failed);
    EXPECT_GT(f1.seen_cnt, 42u);
}

//-----------------------------------------------------------------------------

TEST(FunctionTest, require_that_constant_double_value_can_be_pre_calculated)
{
    auto expect = GenSpec(42).gen();
    auto f = Function::parse("21+21");
    ASSERT_TRUE(!f->has_error());
    const Node &root = f->root();
    auto value = root.get_const_value();
    ASSERT_TRUE(value);
    EXPECT_EQ(spec_from_value(*value), expect);
}

TEST(FunctionTest, require_that_constant_tensor_value_can_be_pre_calculated)
{
    auto expect = GenSpec().idx("x", 10).gen();
    auto f = Function::parse("concat(tensor(x[4])(x+1),tensor(x[6])(x+5),x)");
    ASSERT_TRUE(!f->has_error());
    const Node &root = f->root();
    auto value = root.get_const_value();
    ASSERT_TRUE(value);
    EXPECT_EQ(spec_from_value(*value), expect);
}

TEST(FunctionTest, require_that_non_const_value_cannot_be_pre_calculated)
{
    auto f = Function::parse("a+b");
    ASSERT_TRUE(!f->has_error());
    const Node &root = f->root();
    auto value = root.get_const_value();
    EXPECT_TRUE(value.get() == nullptr);
}

TEST(FunctionTest, require_that_parse_error_does_not_produce_a_const_value)
{
    auto f = Function::parse("this is a parse error");
    EXPECT_TRUE(f->has_error());
    const Node &root = f->root();
    auto value = root.get_const_value();
    EXPECT_TRUE(value.get() == nullptr);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
