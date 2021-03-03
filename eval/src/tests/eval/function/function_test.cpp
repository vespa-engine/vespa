// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/eval/eval/node_traverser.h>
#include <set>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/eval/eval/check_type.h>

using namespace vespalib::eval;
using namespace vespalib::eval::nodes;

std::vector<vespalib::string> params({"x", "y", "z", "w"});

double as_number(const Function &f) {
    auto number = as<Number>(f.root());
    ASSERT_TRUE(number);
    return number->value();
}

vespalib::string as_string(const Function &f) {
    auto string = as<String>(f.root());
    ASSERT_TRUE(string);
    return string->value();
}

struct OperatorLayer {
    Operator::Order order;
    std::vector<vespalib::string> op_names;
};

Operator_UP create_op(vespalib::string name) {
    Operator_UP op = OperatorRepo::instance().create(name);
    ASSERT_TRUE(op.get() != nullptr);
    EXPECT_EQUAL(name, op->op_str());
    return op;
}

void verify_operator_binding_order(std::initializer_list<OperatorLayer> layers) {
    std::set<vespalib::string> seen_names;
    int layer_idx = 0;
    for (OperatorLayer layer: layers) {
        ++layer_idx;
        for (vespalib::string op_name: layer.op_names) {
            seen_names.insert(op_name);
            int other_layer_idx = 0;
            for (OperatorLayer other_layer: layers) {
                ++other_layer_idx;
                for (vespalib::string other_op_name: other_layer.op_names) {
                    Operator_UP op = create_op(op_name);
                    Operator_UP other_op = create_op(other_op_name);
                    bool do_op_before_other_op = (layer_idx < other_layer_idx)
                                                 || ((layer_idx == other_layer_idx)
                                                         && (layer.order == Operator::Order::LEFT));
                    if (!EXPECT_EQUAL(do_op_before_other_op, op->do_before(*other_op))) {
                        fprintf(stderr, "error: left operator '%s' should %sbind before right operator '%s'\n",
                                op->op_str().c_str(), do_op_before_other_op? "" : "not ", other_op->op_str().c_str());
                    }
                }
            }
        }
    }
    auto all_names = OperatorRepo::instance().get_names();
    for (auto name: all_names) {
        if (!EXPECT_EQUAL(1u, seen_names.count(name))) {
            fprintf(stderr, "error: operator '%s' not verified by binding order test\n", name.c_str());
        }
    }
}

bool verify_string(const vespalib::string &str, const vespalib::string &expr) {
    bool ok = true;
    ok &= EXPECT_EQUAL(str, as_string(*Function::parse(params, expr)));
    ok &= EXPECT_EQUAL(expr, Function::parse(params, expr)->dump());
    return ok;
}

void verify_error(const vespalib::string &expr, const vespalib::string &expected_error) {
    auto function = Function::parse(params, expr);
    EXPECT_TRUE(function->has_error());
    EXPECT_EQUAL(expected_error, function->get_error());
}

void verify_parse(const vespalib::string &expr, const vespalib::string &expect) {
    auto function = Function::parse(expr);
    EXPECT_TRUE(!function->has_error());
    EXPECT_EQUAL(function->dump_as_lambda(), expect);
}

TEST("require that scientific numbers can be parsed") {
    EXPECT_EQUAL(1.0,     as_number(*Function::parse(params, "1")));
    EXPECT_EQUAL(2.5,     as_number(*Function::parse(params, "2.5")));
    EXPECT_EQUAL(100.0,   as_number(*Function::parse(params, "100")));
    EXPECT_EQUAL(0.01,    as_number(*Function::parse(params, "0.01")));
    EXPECT_EQUAL(1.05e5,  as_number(*Function::parse(params, "1.05e5")));
    EXPECT_EQUAL(3e7,     as_number(*Function::parse(params, "3e7")));
    EXPECT_EQUAL(1.05e5,  as_number(*Function::parse(params, "1.05e+5")));
    EXPECT_EQUAL(3e7,     as_number(*Function::parse(params, "3e+7")));
    EXPECT_EQUAL(1.05e-5, as_number(*Function::parse(params, "1.05e-5")));
    EXPECT_EQUAL(3e-7,    as_number(*Function::parse(params, "3e-7")));
    EXPECT_EQUAL(1.05e5,  as_number(*Function::parse(params, "1.05E5")));
    EXPECT_EQUAL(3e7,     as_number(*Function::parse(params, "3E7")));
    EXPECT_EQUAL(1.05e5,  as_number(*Function::parse(params, "1.05E+5")));
    EXPECT_EQUAL(3e7,     as_number(*Function::parse(params, "3E+7")));
    EXPECT_EQUAL(1.05e-5, as_number(*Function::parse(params, "1.05E-5")));
    EXPECT_EQUAL(3e-7,    as_number(*Function::parse(params, "3E-7")));
}

TEST("require that number parsing does not eat +/- operators") {
    EXPECT_EQUAL("(((1+2)+3)+4)", Function::parse(params, "1+2+3+4")->dump());
    EXPECT_EQUAL("(((1-2)-3)-4)", Function::parse(params, "1-2-3-4")->dump());
    EXPECT_EQUAL("(((1+x)+3)+y)", Function::parse(params, "1+x+3+y")->dump());
    EXPECT_EQUAL("(((1-x)-3)-y)", Function::parse(params, "1-x-3-y")->dump());
}

TEST("require that symbols can be parsed") {
    EXPECT_EQUAL("x", Function::parse(params, "x")->dump());
    EXPECT_EQUAL("y", Function::parse(params, "y")->dump());
    EXPECT_EQUAL("z", Function::parse(params, "z")->dump());
}

TEST("require that parenthesis can be parsed") {
    EXPECT_EQUAL("x", Function::parse(params, "(x)")->dump());
    EXPECT_EQUAL("x", Function::parse(params, "((x))")->dump());
    EXPECT_EQUAL("x", Function::parse(params, "(((x)))")->dump());
}

TEST("require that strings are parsed and dumped correctly") {
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
        vespalib::string raw_expr = vespalib::make_string("\"%c\"", c);
        vespalib::string hex_expr = vespalib::make_string("\"\\x%02x\"", c);
        vespalib::string raw_str = vespalib::make_string("%c", c);
        EXPECT_EQUAL(raw_str, as_string(*Function::parse(params, hex_expr)));
        if (c != 0 && c != '\"' && c != '\\') {
            EXPECT_EQUAL(raw_str, as_string(*Function::parse(params, raw_expr)));
        } else {
            EXPECT_TRUE(Function::parse(params, raw_expr)->has_error());
        }
        if (c == '\\') {
            EXPECT_EQUAL("\"\\\\\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\"') {
            EXPECT_EQUAL("\"\\\"\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\t') {
            EXPECT_EQUAL("\"\\t\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\n') {
            EXPECT_EQUAL("\"\\n\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\r') {
            EXPECT_EQUAL("\"\\r\"", Function::parse(params, hex_expr)->dump());
        } else if (c == '\f') {
            EXPECT_EQUAL("\"\\f\"", Function::parse(params, hex_expr)->dump());
        } else if ((c >= 32) && (c <= 126)) {
            if (c >= 'a' && c <= 'z' && c != 't' && c != 'n' && c != 'r' && c != 'f') {
                EXPECT_TRUE(Function::parse(params, vespalib::make_string("\"\\%c\"", c))->has_error());
            }
            EXPECT_EQUAL(raw_expr, Function::parse(params, hex_expr)->dump());
        } else {
            EXPECT_EQUAL(hex_expr, Function::parse(params, hex_expr)->dump());
        }
    }
}

TEST("require that strings with single quotes can be parsed") {
    EXPECT_EQUAL(Function::parse("'foo'")->dump(), "\"foo\"");
    EXPECT_EQUAL(Function::parse("'fo\\'o'")->dump(), "\"fo'o\"");
}

TEST("require that free arrays cannot be parsed") {
    verify_error("[1,2,3]", "[]...[missing value]...[[1,2,3]]");
}

TEST("require that negative values can be parsed") {
    EXPECT_EQUAL("-1", Function::parse(params, "-1")->dump());
    EXPECT_EQUAL("1", Function::parse(params, "--1")->dump());
    EXPECT_EQUAL("-1", Function::parse(params, " ( - ( - ( - ( (1) ) ) ) )")->dump());
    EXPECT_EQUAL("-2.5", Function::parse(params, "-2.5")->dump());
    EXPECT_EQUAL("-100", Function::parse(params, "-100")->dump());
}

TEST("require that negative symbols can be parsed") {
    EXPECT_EQUAL("(-x)", Function::parse(params, "-x")->dump());
    EXPECT_EQUAL("(-y)", Function::parse(params, "-y")->dump());
    EXPECT_EQUAL("(-z)", Function::parse(params, "-z")->dump());
    EXPECT_EQUAL("(-(-(-x)))", Function::parse(params, "---x")->dump());
}

TEST("require that not can be parsed") {
    EXPECT_EQUAL("(!x)", Function::parse(params, "!x")->dump());
    EXPECT_EQUAL("(!(!x))", Function::parse(params, "!!x")->dump());
    EXPECT_EQUAL("(!(!(!x)))", Function::parse(params, "!!!x")->dump());
}

TEST("require that not/neg binds to next value") {
    EXPECT_EQUAL("((!(!(-(-x))))^z)", Function::parse(params, "!!--x^z")->dump());
    EXPECT_EQUAL("((-(-(!(!x))))^z)", Function::parse(params, "--!!x^z")->dump());
    EXPECT_EQUAL("((!(-(-(!x))))^z)", Function::parse(params, "!--!x^z")->dump());
    EXPECT_EQUAL("((-(!(!(-x))))^z)", Function::parse(params, "-!!-x^z")->dump());
}

TEST("require that parenthesis resolves before not/neg") {
    EXPECT_EQUAL("(!(x^z))", Function::parse(params, "!(x^z)")->dump());
    EXPECT_EQUAL("(-(x^z))", Function::parse(params, "-(x^z)")->dump());
}

TEST("require that operators have appropriate binding order") {
    verify_operator_binding_order({    { Operator::Order::RIGHT, { "^" } },
                                       { Operator::Order::LEFT,  { "*", "/", "%" } },
                                       { Operator::Order::LEFT,  { "+", "-" } },
                                       { Operator::Order::LEFT,  { "==", "!=", "~=", "<", "<=", ">", ">=" } },
                                       { Operator::Order::LEFT,  { "&&" } },
                                       { Operator::Order::LEFT,  { "||" } } });
}

TEST("require that operators binding left are calculated left to right") {
    EXPECT_TRUE(create_op("+")->order() == Operator::Order::LEFT);
    EXPECT_EQUAL("((x+y)+z)", Function::parse(params, "x+y+z")->dump());
}

TEST("require that operators binding right are calculated right to left") {
    EXPECT_TRUE(create_op("^")->order() == Operator::Order::RIGHT);
    EXPECT_EQUAL("(x^(y^z))", Function::parse(params, "x^y^z")->dump());
}

TEST("require that operators with higher precedence are resolved first") {
    EXPECT_TRUE(create_op("*")->priority() > create_op("+")->priority());
    EXPECT_EQUAL("(x+(y*z))", Function::parse(params, "x+y*z")->dump());
    EXPECT_EQUAL("((x*y)+z)", Function::parse(params, "x*y+z")->dump());
}

TEST("require that multi-level operator precedence resolving works") {
    EXPECT_TRUE(create_op("^")->priority() > create_op("*")->priority());
    EXPECT_TRUE(create_op("*")->priority() > create_op("+")->priority());
    EXPECT_EQUAL("(x+(y*(z^w)))", Function::parse(params, "x+y*z^w")->dump());
    EXPECT_EQUAL("(x+((y^z)*w))", Function::parse(params, "x+y^z*w")->dump());
    EXPECT_EQUAL("((x*y)+(z^w))", Function::parse(params, "x*y+z^w")->dump());
    EXPECT_EQUAL("((x*(y^z))+w)", Function::parse(params, "x*y^z+w")->dump());
    EXPECT_EQUAL("((x^y)+(z*w))", Function::parse(params, "x^y+z*w")->dump());
    EXPECT_EQUAL("(((x^y)*z)+w)", Function::parse(params, "x^y*z+w")->dump());
}

TEST("require that expressions are combined when parenthesis are closed") {
    EXPECT_EQUAL("((x+(y+z))+w)", Function::parse(params, "x+(y+z)+w")->dump());
}

TEST("require that operators can not bind out of parenthesis") {
    EXPECT_TRUE(create_op("*")->priority() > create_op("+")->priority());
    EXPECT_EQUAL("((x+y)*(x+z))", Function::parse(params, "(x+y)*(x+z)")->dump());
}

TEST("require that set membership constructs can be parsed") {
    EXPECT_EQUAL("(x in [1,2,3])", Function::parse(params, "x in [1,2,3]")->dump());
    EXPECT_EQUAL("(x in [1,2,3])", Function::parse(params, "x  in  [ 1 , 2 , 3 ] ")->dump());
    EXPECT_EQUAL("(x in [-1,-2,-3])", Function::parse(params, "x in [-1,-2,-3]")->dump());
    EXPECT_EQUAL("(x in [-1,-2,-3])", Function::parse(params, "x in [ - 1 , - 2 , - 3 ]")->dump());
    EXPECT_EQUAL("(x in [1,2,3])", Function::parse(params, "x  in[1,2,3]")->dump());
    EXPECT_EQUAL("(x in [1,2,3])", Function::parse(params, "(x)in[1,2,3]")->dump());
    EXPECT_EQUAL("(x in [\"a\",2,\"c\"])", Function::parse(params, "x in [\"a\",2,\"c\"]")->dump());
}

TEST("require that set membership entries must be array of strings/numbers") {
    verify_error("x in 1", "[x in ]...[expected '[', but got '1']...[1]");
    verify_error("x in ([1])", "[x in ]...[expected '[', but got '(']...[([1])]");
    verify_error("x in [y]", "[x in [y]...[invalid entry for 'in' operator]...[]]");
    verify_error("x in [!1]", "[x in [!1]...[invalid entry for 'in' operator]...[]]");
    verify_error("x in [1+2]", "[x in [1]...[expected ',', but got '+']...[+2]]");
    verify_error("x in [-\"foo\"]", "[x in [-\"foo\"]...[invalid entry for 'in' operator]...[]]");
}

TEST("require that set membership binds to the next value") {
    EXPECT_EQUAL("((x in [1,2,3])^2)", Function::parse(params, "x in [1,2,3]^2")->dump());
}

TEST("require that set membership binds to the left with appropriate precedence") {
    EXPECT_EQUAL("((x<y) in [1,2,3])", Function::parse(params, "x < y in [1,2,3]")->dump());
    EXPECT_EQUAL("(x&&(y in [1,2,3]))", Function::parse(params, "x && y in [1,2,3]")->dump());
}

TEST("require that function calls can be parsed") {
    EXPECT_EQUAL("min(max(x,y),sqrt(z))", Function::parse(params, "min(max(x,y),sqrt(z))")->dump());
}

TEST("require that if expressions can be parsed") {
    EXPECT_EQUAL("if(x,y,z)", Function::parse(params, "if(x,y,z)")->dump());
    EXPECT_EQUAL("if(x,y,z)", Function::parse(params, "if (x,y,z)")->dump());
    EXPECT_EQUAL("if(x,y,z)", Function::parse(params, " if ( x , y , z ) ")->dump());
    EXPECT_EQUAL("if(((x>1)&&(y<3)),(y+1),(z-1))", Function::parse(params, "if(x>1&&y<3,y+1,z-1)")->dump());
    EXPECT_EQUAL("if(if(x,y,z),if(x,y,z),if(x,y,z))", Function::parse(params, "if(if(x,y,z),if(x,y,z),if(x,y,z))")->dump());
    EXPECT_EQUAL("if(x,y,z,0.25)", Function::parse(params, "if(x,y,z,0.25)")->dump());
    EXPECT_EQUAL("if(x,y,z,0.75)", Function::parse(params, "if(x,y,z,0.75)")->dump());
}

TEST("require that if probability can be inspected") {
    auto fun_1 = Function::parse("if(x,y,z,0.25)");
    auto if_1 = as<If>(fun_1->root());
    ASSERT_TRUE(if_1);
    EXPECT_EQUAL(0.25, if_1->p_true());
    auto fun_2 = Function::parse("if(x,y,z,0.75)");
    auto if_2 = as<If>(fun_2->root());
    ASSERT_TRUE(if_2);
    EXPECT_EQUAL(0.75, if_2->p_true());
}

TEST("require that symbols can be implicit") {
    EXPECT_EQUAL("x", Function::parse("x")->dump());
    EXPECT_EQUAL("y", Function::parse("y")->dump());
    EXPECT_EQUAL("z", Function::parse("z")->dump());
}

TEST("require that implicit parameters are picket up left to right") {
    auto fun1 = Function::parse("x+y+y");
    auto fun2 = Function::parse("y+y+x");
    EXPECT_EQUAL("((x+y)+y)", fun1->dump());
    EXPECT_EQUAL("((y+y)+x)", fun2->dump());
    ASSERT_EQUAL(2u, fun1->num_params());
    ASSERT_EQUAL(2u, fun2->num_params());
    EXPECT_EQUAL("x", fun1->param_name(0));
    EXPECT_EQUAL("x", fun2->param_name(1));
    EXPECT_EQUAL("y", fun1->param_name(1));
    EXPECT_EQUAL("y", fun2->param_name(0));
}

//-----------------------------------------------------------------------------

TEST("require that leaf nodes have no children") {
    EXPECT_TRUE(Function::parse("123")->root().is_leaf());
    EXPECT_TRUE(Function::parse("x")->root().is_leaf());
    EXPECT_TRUE(Function::parse("\"abc\"")->root().is_leaf());
    EXPECT_EQUAL(0u, Function::parse("123")->root().num_children());
    EXPECT_EQUAL(0u, Function::parse("x")->root().num_children());
    EXPECT_EQUAL(0u, Function::parse("\"abc\"")->root().num_children());
}

TEST("require that Neg child can be accessed") {
    auto f = Function::parse("-x");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQUAL(1u, root.num_children());
    EXPECT_TRUE(root.get_child(0).is_param());
}

TEST("require that Not child can be accessed") {
    auto f = Function::parse("!1");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQUAL(1u, root.num_children());
    EXPECT_EQUAL(1.0, root.get_child(0).get_const_value());
}

TEST("require that If children can be accessed") {
    auto f = Function::parse("if(1,2,3)");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQUAL(3u, root.num_children());
    EXPECT_EQUAL(1.0, root.get_child(0).get_const_value());
    EXPECT_EQUAL(2.0, root.get_child(1).get_const_value());
    EXPECT_EQUAL(3.0, root.get_child(2).get_const_value());
}

TEST("require that Operator children can be accessed") {
    auto f = Function::parse("1+2");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQUAL(2u, root.num_children());
    EXPECT_EQUAL(1.0, root.get_child(0).get_const_value());
    EXPECT_EQUAL(2.0, root.get_child(1).get_const_value());
}

TEST("require that Call children can be accessed") {
    auto f = Function::parse("max(1,2)");
    const Node &root = f->root();
    EXPECT_TRUE(!root.is_leaf());
    ASSERT_EQUAL(2u, root.num_children());
    EXPECT_EQUAL(1.0, root.get_child(0).get_const_value());
    EXPECT_EQUAL(2.0, root.get_child(1).get_const_value());
}

struct MyNodeHandler : public NodeHandler {
    std::vector<nodes::Node_UP> nodes;
    void handle(nodes::Node_UP node) override {
        if (node.get() != nullptr) {
            nodes.push_back(std::move(node));
        }
    }
};

size_t detach_from_root(const vespalib::string &expr) {
    MyNodeHandler handler;
    auto function = Function::parse(expr);
    nodes::Node &mutable_root = const_cast<nodes::Node&>(function->root());
    mutable_root.detach_children(handler);
    return handler.nodes.size();
}

TEST("require that children can be detached") {
    EXPECT_EQUAL(0u, detach_from_root("1"));
    EXPECT_EQUAL(0u, detach_from_root("a"));
    EXPECT_EQUAL(1u, detach_from_root("-a"));
    EXPECT_EQUAL(1u, detach_from_root("!a"));
    EXPECT_EQUAL(3u, detach_from_root("if(1,2,3)"));
    EXPECT_EQUAL(1u, detach_from_root("a in [1,2,3,4,5]"));
    EXPECT_EQUAL(2u, detach_from_root("a+b"));
    EXPECT_EQUAL(1u, detach_from_root("isNan(a)"));
    EXPECT_EQUAL(2u, detach_from_root("max(a,b)"));
}

//-----------------------------------------------------------------------------

struct MyTraverser : public NodeTraverser {
    size_t open_true_cnt;
    std::vector<std::pair<bool, const nodes::Node &> > history;
    explicit MyTraverser(size_t open_true_cnt_in)
        : open_true_cnt(open_true_cnt_in), history() {}
    ~MyTraverser() override;
    virtual bool open(const nodes::Node &node) override {
        history.emplace_back(true, node);
        if (open_true_cnt == 0) {
            return false;
        }
        --open_true_cnt;
        return true;
    }
    virtual void close(const nodes::Node &node) override {
        history.emplace_back(false, node);
    }
    void verify(const nodes::Node &node, size_t &offset, size_t &open_cnt) {
        ASSERT_TRUE(history.size() > offset);
        EXPECT_TRUE(history[offset].first);
        EXPECT_EQUAL(&node, &history[offset].second);
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
        EXPECT_EQUAL(&node, &history[offset].second);
        ++offset;
    }
};

MyTraverser::~MyTraverser() = default;

size_t verify_traversal(size_t open_true_cnt, const vespalib::string &expression) {
    auto function = Function::parse(expression);
    if (!EXPECT_TRUE(!function->has_error())) {
        fprintf(stderr, "--> %s\n", function->get_error().c_str());
    }
    MyTraverser traverser(open_true_cnt);
    function->root().traverse(traverser);
    size_t offset = 0;
    size_t open_cnt = open_true_cnt;
    traverser.verify(function->root(), offset, open_cnt);
    EXPECT_EQUAL(offset, traverser.history.size());
    return offset;
}

bool verify_expression_traversal(const vespalib::string &expression) {
    for (size_t open_cnt = 0; true; ++open_cnt) {
        size_t num_callbacks = verify_traversal(open_cnt, expression);
        if (num_callbacks == (open_cnt * 2)) { // graph is now fully expanded
            return EXPECT_EQUAL(open_cnt * 2, verify_traversal(open_cnt + 1, expression));
        }
    }
}

TEST("require that traversal works as expected") {
    EXPECT_TRUE(verify_expression_traversal("1"));
    EXPECT_TRUE(verify_expression_traversal("1+2"));
    EXPECT_TRUE(verify_expression_traversal("1+2*3-4/5"));
    EXPECT_TRUE(verify_expression_traversal("if(x,1+2*3,if(a,b,c)/5)"));
}

//-----------------------------------------------------------------------------

TEST("require that node types can be checked") {
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

TEST("require that parameter is param, but not const") {
    EXPECT_TRUE(Function::parse("x")->root().is_param());
    EXPECT_TRUE(!Function::parse("x")->root().is_const());
}

TEST("require that inverted parameter is not param") {
    EXPECT_TRUE(!Function::parse("-x")->root().is_param());
}

TEST("require that number is const, but not param") {
    EXPECT_TRUE(Function::parse("123")->root().is_const());
    EXPECT_TRUE(!Function::parse("123")->root().is_param());
}

TEST("require that string is const") {
    EXPECT_TRUE(Function::parse("\"x\"")->root().is_const());
}

TEST("require that neg is const if sub-expression is const") {
    EXPECT_TRUE(Function::parse("-123")->root().is_const());
    EXPECT_TRUE(!Function::parse("-x")->root().is_const());
}

TEST("require that not is const if sub-expression is const") {
    EXPECT_TRUE(Function::parse("!1")->root().is_const());
    EXPECT_TRUE(!Function::parse("!x")->root().is_const());
}

TEST("require that operators are cost if both children are const") {
    EXPECT_TRUE(!Function::parse("x+y")->root().is_const());
    EXPECT_TRUE(!Function::parse("1+y")->root().is_const());
    EXPECT_TRUE(!Function::parse("x+2")->root().is_const());
    EXPECT_TRUE(Function::parse("1+2")->root().is_const());
}

TEST("require that set membership is never tagged as const (NB: avoids jit recursion)") {
    EXPECT_TRUE(!Function::parse("x in [x,y,z]")->root().is_const());
    EXPECT_TRUE(!Function::parse("1 in [x,y,z]")->root().is_const());
    EXPECT_TRUE(!Function::parse("1 in [1,y,z]")->root().is_const());
    EXPECT_TRUE(!Function::parse("1 in [1,2,3]")->root().is_const());
}

TEST("require that calls are cost if all parameters are const") {
    EXPECT_TRUE(!Function::parse("max(x,y)")->root().is_const());
    EXPECT_TRUE(!Function::parse("max(1,y)")->root().is_const());
    EXPECT_TRUE(!Function::parse("max(x,2)")->root().is_const());
    EXPECT_TRUE(Function::parse("max(1,2)")->root().is_const());
}

//-----------------------------------------------------------------------------

TEST("require that feature less than constant is tree if children are trees or constants") {
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

TEST("require that feature in set of constants is tree if children are trees or constants") {
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], 3, 4)")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], if(bar < 3, 4, 5), 6)")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], if(bar < 3, 4, 5), if(baz < 6, 7, 8))")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], 3, if(baz < 4, 5, 6))")->root().is_tree());
    EXPECT_TRUE(Function::parse("if (foo in [1, 2], min(1,3), max(1,4))")->root().is_tree());    
    EXPECT_TRUE(!Function::parse("if (1 in [1, 2], 3, 4)")->root().is_tree());
}

TEST("require that sums of trees and forests are forests") {
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
    vespalib::string wrapper;
    vespalib::string body;
    vespalib::string error;
    ~UnWrapped();
};


UnWrapped::~UnWrapped() {}

UnWrapped unwrap(const vespalib::string &str) {
    UnWrapped result;
    bool ok = Function::unwrap(str, result.wrapper, result.body, result.error);
    EXPECT_EQUAL(ok, result.error.empty());
    return result;
}

TEST("require that unwrapping works") {
    EXPECT_EQUAL("max", unwrap("max(x+y)").wrapper);
    EXPECT_EQUAL("max", unwrap("  max(x+y)").wrapper);
    EXPECT_EQUAL("max", unwrap("  max  (x+y)").wrapper);
    EXPECT_EQUAL("x+y", unwrap("max(x+y)").body);
    EXPECT_EQUAL("x+y", unwrap("max(x+y)  ").body);
    EXPECT_EQUAL("max", unwrap("max()").wrapper);
    EXPECT_EQUAL("", unwrap("max()").body);
    EXPECT_EQUAL("", unwrap("max()").error);
    EXPECT_EQUAL("could not extract wrapper name", unwrap("").error);
    EXPECT_EQUAL("could not extract wrapper name", unwrap("(x+y)").error);
    EXPECT_EQUAL("could not extract wrapper name", unwrap("  (x+y)").error);
    EXPECT_EQUAL("could not match opening '('", unwrap("max").error);
    EXPECT_EQUAL("could not match opening '('", unwrap("max)").error);
    EXPECT_EQUAL("could not match opening '('", unwrap("max5(x+y)").error);
    EXPECT_EQUAL("could not match opening '('", unwrap("max)x+y(").error);
    EXPECT_EQUAL("could not match closing ')'", unwrap("max(x+y").error);
    EXPECT_EQUAL("could not match closing ')'", unwrap("max(x+y)x").error);
    EXPECT_EQUAL("could not match closing ')'", unwrap("max(").error);
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
                        const char *&pos_out, vespalib::string &symbol_out) const override
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

TEST("require that custom symbol extractor may be used") {
    EXPECT_EQUAL("[x+]...[missing value]...[*y]", Function::parse(params, "x+*y")->dump());
    EXPECT_EQUAL("[x+]...[missing value]...[*y]", Function::parse(params, "x+*y", MySymbolExtractor())->dump());
    EXPECT_EQUAL("[x+]...[unknown symbol: 'x+']...[*y]", Function::parse(params, "x+*y", MySymbolExtractor({'+'}))->dump());
    EXPECT_EQUAL("[x+*y]...[unknown symbol: 'x+*y']...[]", Function::parse(params, "x+*y", MySymbolExtractor({'+', '*'}))->dump());
}

TEST("require that unknown function works as expected  with custom symbol extractor") {
    EXPECT_EQUAL("[bogus(]...[unknown function: 'bogus']...[x)+y]", Function::parse(params, "bogus(x)+y")->dump());    
    EXPECT_EQUAL("[bogus]...[unknown symbol: 'bogus']...[(x)+y]", Function::parse(params, "bogus(x)+y", MySymbolExtractor())->dump());
    EXPECT_EQUAL("[bogus(x)]...[unknown symbol: 'bogus(x)']...[+y]", Function::parse(params, "bogus(x)+y", MySymbolExtractor({'(', ')'}))->dump());
}

TEST("require that unknown function that is valid parameter works as expected with custom symbol extractor") {
    EXPECT_EQUAL("[z(]...[unknown function: 'z']...[x)+y]", Function::parse(params, "z(x)+y")->dump());
    EXPECT_EQUAL("[z]...[invalid operator: '(']...[(x)+y]", Function::parse(params, "z(x)+y", MySymbolExtractor())->dump());
    EXPECT_EQUAL("[z(x)]...[unknown symbol: 'z(x)']...[+y]", Function::parse(params, "z(x)+y", MySymbolExtractor({'(', ')'}))->dump());
}

TEST("require that custom symbol extractor is not invoked for known function call") {
    MySymbolExtractor extractor;
    EXPECT_EQUAL(extractor.invoke_count, 0u);
    EXPECT_EQUAL("[bogus]...[unknown symbol: 'bogus']...[(1,2)]", Function::parse(params, "bogus(1,2)", extractor)->dump());
    EXPECT_EQUAL(extractor.invoke_count, 1u);
    EXPECT_EQUAL("max(1,2)", Function::parse(params, "max(1,2)", extractor)->dump());
    EXPECT_EQUAL(extractor.invoke_count, 1u);
}

//-----------------------------------------------------------------------------

TEST("require that valid function does not report parse error") {
    auto function = Function::parse(params, "x + y");
    EXPECT_TRUE(!function->has_error());
    EXPECT_EQUAL("", function->get_error());    
}

TEST("require that an invalid function with explicit paramers retain its parameters") {
    auto function = Function::parse({"x", "y"}, "x & y");
    EXPECT_TRUE(function->has_error());
    ASSERT_EQUAL(2u, function->num_params());
    ASSERT_EQUAL("x", function->param_name(0));
    ASSERT_EQUAL("y", function->param_name(1));
}

TEST("require that an invalid function with implicit paramers has no parameters") {
    auto function = Function::parse("x & y");
    EXPECT_TRUE(function->has_error());
    EXPECT_EQUAL(0u, function->num_params());
}

TEST("require that unknown operator gives parse error") {
    verify_error("x&y", "[x]...[invalid operator: '&']...[&y]");
}

TEST("require that unknown symbol gives parse error") {
    verify_error("x+a", "[x+a]...[unknown symbol: 'a']...[]");
}

TEST("require that missing value gives parse error") {
    verify_error("x+", "[x+]...[missing value]...[]");
    verify_error("x++y", "[x+]...[missing value]...[+y]");
    verify_error("x+++y", "[x+]...[missing value]...[++y]");
    verify_error("x+(y+)+z", "[x+(y+]...[missing value]...[)+z]");
}

//-----------------------------------------------------------------------------

TEST("require that tensor operations can be nested") {
    EXPECT_EQUAL("reduce(reduce(reduce(a,sum),sum),sum,dim)",
                 Function::parse("reduce(reduce(reduce(a,sum),sum),sum,dim)")->dump());
}

//-----------------------------------------------------------------------------

TEST("require that tensor map can be parsed") {
    EXPECT_EQUAL("map(a,f(x)(x+1))", Function::parse("map(a,f(x)(x+1))")->dump());
    EXPECT_EQUAL("map(a,f(x)(x+1))", Function::parse(" map ( a , f ( x ) ( x + 1 ) ) ")->dump());
}

TEST("require that tensor join can be parsed") {
    EXPECT_EQUAL("join(a,b,f(x,y)(x+y))", Function::parse("join(a,b,f(x,y)(x+y))")->dump());
    EXPECT_EQUAL("join(a,b,f(x,y)(x+y))", Function::parse(" join ( a , b , f ( x , y ) ( x + y ) ) ")->dump());
}

TEST("require that parenthesis are added around lambda expression when needed") {
    EXPECT_EQUAL("f(x)(sin(x))", Function::parse("sin(x)")->dump_as_lambda());
}

TEST("require that parse error inside a lambda fails the enclosing expression") {
    verify_error("map(x,f(a)(b))", "[map(x,f(a)(b]...[unknown symbol: 'b']...[))]");
}

TEST("require that outer parameters are hidden within a lambda") {
    verify_error("map(x,f(a)(y))", "[map(x,f(a)(y]...[unknown symbol: 'y']...[))]");
}

//-----------------------------------------------------------------------------

TEST("require that tensor reduce can be parsed") {
    EXPECT_EQUAL("reduce(x,sum,a,b)", Function::parse({"x"}, "reduce(x,sum,a,b)")->dump());
    EXPECT_EQUAL("reduce(x,sum,a,b,c)", Function::parse({"x"}, "reduce(x,sum,a,b,c)")->dump());
    EXPECT_EQUAL("reduce(x,sum,a,b,c)", Function::parse({"x"}, " reduce ( x , sum , a , b , c ) ")->dump());
    EXPECT_EQUAL("reduce(x,sum)", Function::parse({"x"}, "reduce(x,sum)")->dump());
    EXPECT_EQUAL("reduce(x,avg)", Function::parse({"x"}, "reduce(x,avg)")->dump());
    EXPECT_EQUAL("reduce(x,avg)", Function::parse({"x"}, "reduce( x , avg )")->dump());
    EXPECT_EQUAL("reduce(x,count)", Function::parse({"x"}, "reduce(x,count)")->dump());
    EXPECT_EQUAL("reduce(x,prod)", Function::parse({"x"}, "reduce(x,prod)")->dump());
    EXPECT_EQUAL("reduce(x,min)", Function::parse({"x"}, "reduce(x,min)")->dump());
    EXPECT_EQUAL("reduce(x,max)", Function::parse({"x"}, "reduce(x,max)")->dump());
}

TEST("require that tensor reduce with unknown aggregator fails") {
    verify_error("reduce(x,bogus)", "[reduce(x,bogus]...[unknown aggregator: 'bogus']...[)]");
}

TEST("require that tensor reduce with duplicate dimensions fails") {
    verify_error("reduce(x,sum,a,a)", "[reduce(x,sum,a,a]...[duplicate identifiers]...[)]");
}

//-----------------------------------------------------------------------------

TEST("require that tensor rename can be parsed") {
    EXPECT_EQUAL("rename(x,a,b)", Function::parse({"x"}, "rename(x,a,b)")->dump());
    EXPECT_EQUAL("rename(x,a,b)", Function::parse({"x"}, "rename(x,(a),(b))")->dump());
    EXPECT_EQUAL("rename(x,a,b)", Function::parse({"x"}, "rename(x,a,(b))")->dump());
    EXPECT_EQUAL("rename(x,a,b)", Function::parse({"x"}, "rename(x,(a),b)")->dump());
    EXPECT_EQUAL("rename(x,(a,b),(b,a))", Function::parse({"x"}, "rename(x,(a,b),(b,a))")->dump());
    EXPECT_EQUAL("rename(x,a,b)", Function::parse({"x"}, "rename( x , a , b )")->dump());
    EXPECT_EQUAL("rename(x,a,b)", Function::parse({"x"}, "rename( x , ( a ) , ( b ) )")->dump());
    EXPECT_EQUAL("rename(x,(a,b),(b,a))", Function::parse({"x"}, "rename( x , ( a , b ) , ( b , a ) )")->dump());
}

TEST("require that tensor rename dimension lists cannot be empty") {
    verify_error("rename(x,,b)", "[rename(x,]...[missing identifier]...[,b)]");
    verify_error("rename(x,a,)", "[rename(x,a,]...[missing identifier]...[)]");
    verify_error("rename(x,(),b)", "[rename(x,()]...[missing identifiers]...[,b)]");
    verify_error("rename(x,a,())", "[rename(x,a,()]...[missing identifiers]...[)]");
}

TEST("require that tensor rename dimension lists cannot contain duplicates") {
    verify_error("rename(x,(a,a),(b,a))", "[rename(x,(a,a)]...[duplicate identifiers]...[,(b,a))]");
    verify_error("rename(x,(a,b),(b,b))", "[rename(x,(a,b),(b,b)]...[duplicate identifiers]...[)]");
}

TEST("require that tensor rename dimension lists must have equal size") {
    verify_error("rename(x,(a,b),(b))", "[rename(x,(a,b),(b)]...[dimension list size mismatch]...[)]");
    verify_error("rename(x,(a),(b,a))", "[rename(x,(a),(b,a)]...[dimension list size mismatch]...[)]");
}

//-----------------------------------------------------------------------------

TEST("require that tensor lambda can be parsed") {
    EXPECT_EQUAL("tensor(x[3])(x)", Function::parse({}, "tensor(x[3])(x)")->dump());
    EXPECT_EQUAL("tensor(x[2],y[2])(x==y)",
                 Function::parse({}, " tensor ( x [ 2 ] , y [ 2 ] ) ( x == y ) ")->dump());
}

TEST("require that tensor lambda requires appropriate tensor type") {
    verify_error("tensor(x[10],y[])(x==y)", "[tensor(x[10],y[])]...[invalid tensor type]...[(x==y)]");
    verify_error("tensor(x[10],y{})(x==y)", "[tensor(x[10],y{})]...[invalid tensor type]...[(x==y)]");
    verify_error("tensor()(x==y)", "[tensor()]...[invalid tensor type]...[(x==y)]");
}

TEST("require that tensor lambda can use non-dimension symbols") {
    EXPECT_EQUAL("tensor(x[2])(x==a)",
                 Function::parse({"a"}, "tensor(x[2])(x==a)")->dump());
}

//-----------------------------------------------------------------------------

TEST("require that verbose tensor create can be parsed") {
    auto dense = Function::parse("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}");
    auto sparse1 = Function::parse("tensor(x{}):{{x:a}:1,{x:b}:2,{x:c}:3}");
    auto sparse2 = Function::parse("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}");
    auto sparse3 = Function::parse("tensor(x{}):{{x:'a'}:1,{x:'b'}:2,{x:'c'}:3}");
    auto mixed1 = Function::parse("tensor(x{},y[2]):{{x:a,y:0}:1,{x:a,y:1}:2}");
    auto mixed2 = Function::parse("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}");
    EXPECT_EQUAL("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}", dense->dump());
    EXPECT_EQUAL("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse1->dump());
    EXPECT_EQUAL("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse2->dump());
    EXPECT_EQUAL("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse3->dump());
    EXPECT_EQUAL("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed1->dump());
    EXPECT_EQUAL("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed2->dump());
}

TEST("require that verbose tensor create can contain expressions") {
    auto fun = Function::parse("tensor(x[2]):{{x:0}:1,{x:1}:2+a}");
    EXPECT_EQUAL("tensor(x[2]):{{x:0}:1,{x:1}:(2+a)}", fun->dump());
    ASSERT_EQUAL(fun->num_params(), 1u);
    EXPECT_EQUAL(fun->param_name(0), "a");
}

TEST("require that verbose tensor create handles spaces and reordering of various elements") {
    auto fun = Function::parse(" tensor ( y [ 2 ] , x [ 2 ] ) : { { x : 0 , y : 1 } : 2 , "
                               "{ y : 0 , x : 0 } : 1 , { y : 0 , x : 1 } : 3 , { x : 1 , y : 1 } : 4 } ");
    EXPECT_EQUAL("tensor(x[2],y[2]):{{x:0,y:0}:1,{x:0,y:1}:2,{x:1,y:0}:3,{x:1,y:1}:4}", fun->dump());
}

TEST("require that verbose tensor create detects invalid tensor type") {
    TEST_DO(verify_error("tensor(x[,y}):{{ignored}}",
                         "[tensor(x[,y})]...[invalid tensor type]...[:{{ignored}}]"));
}

TEST("require that verbose tensor create detects incomplete addresses") {
    TEST_DO(verify_error("tensor(x[1],y[1]):{{x:0}:1}",
                         "[tensor(x[1],y[1]):{{x:0}]...[incomplete address: '{x:0}']...[:1}]"));
}

TEST("require that verbose tensor create detects invalid dimension names") {
    TEST_DO(verify_error("tensor(x[1]):{{y:0}:1}",
                         "[tensor(x[1]):{{y]...[invalid dimension name: 'y']...[:0}:1}]"));
}

TEST("require that verbose tensor create detects out-of-bounds indexes for indexed dimensions") {
    TEST_DO(verify_error("tensor(x[1]):{{x:1}:1}",
                         "[tensor(x[1]):{{x:1]...[dimension index too large: 1]...[}:1}]"));
}

TEST("require that verbose tensor create detects non-numeric indexes for indexed dimensions") {
    TEST_DO(verify_error("tensor(x[1]):{{x:foo}:1}",
                         "[tensor(x[1]):{{x:]...[expected number]...[foo}:1}]"));
}

TEST("require that verbose tensor create indexes cannot be quoted") {
    TEST_DO(verify_error("tensor(x[1]):{{x:\"1\"}:1}",
                         "[tensor(x[1]):{{x:]...[expected number]...[\"1\"}:1}]"));
    TEST_DO(verify_error("tensor(x[1]):{{x:'1'}:1}",
                         "[tensor(x[1]):{{x:]...[expected number]...['1'}:1}]"));
}

//-----------------------------------------------------------------------------

TEST("require that convenient tensor create can be parsed") {
    auto dense = Function::parse("tensor(x[3]):[1,2,3]");
    auto sparse1 = Function::parse("tensor(x{}):{a:1,b:2,c:3}");
    auto sparse2 = Function::parse("tensor(x{}):{\"a\":1,\"b\":2,\"c\":3}");
    auto sparse3 = Function::parse("tensor(x{}):{'a':1,'b':2,'c':3}");
    auto mixed1 = Function::parse("tensor(x{},y[2]):{a:[1,2]}");
    auto mixed2 = Function::parse("tensor(x{},y[2]):{\"a\":[1,2]}");
    EXPECT_EQUAL("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}", dense->dump());
    EXPECT_EQUAL("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse1->dump());
    EXPECT_EQUAL("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse2->dump());
    EXPECT_EQUAL("tensor(x{}):{{x:\"a\"}:1,{x:\"b\"}:2,{x:\"c\"}:3}", sparse3->dump());
    EXPECT_EQUAL("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed1->dump());
    EXPECT_EQUAL("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2}", mixed2->dump());
}

TEST("require that convenient tensor create can contain expressions") {
    auto fun = Function::parse("tensor(x[2]):[1,2+a]");
    EXPECT_EQUAL("tensor(x[2]):{{x:0}:1,{x:1}:(2+a)}", fun->dump());
    ASSERT_EQUAL(fun->num_params(), 1u);
    EXPECT_EQUAL(fun->param_name(0), "a");
}

TEST("require that convenient tensor create handles dimension order") {
    auto mixed = Function::parse("tensor(y{},x[2]):{a:[1,2]}");
    EXPECT_EQUAL("tensor(x[2],y{}):{{x:0,y:\"a\"}:1,{x:1,y:\"a\"}:2}", mixed->dump());
}

TEST("require that convenient tensor create can be highly nested") {
    vespalib::string expect("tensor(a{},b{},c[1],d[1]):{{a:\"x\",b:\"y\",c:0,d:0}:5}");
    auto nested1 = Function::parse("tensor(a{},b{},c[1],d[1]):{x:{y:[[5]]}}");
    auto nested2 = Function::parse("tensor(c[1],d[1],a{},b{}):[[{x:{y:5}}]]");
    auto nested3 = Function::parse("tensor(a{},c[1],b{},d[1]): { x : [ { y : [ 5 ] } ] } ");
    EXPECT_EQUAL(expect, nested1->dump());
    EXPECT_EQUAL(expect, nested2->dump());
    EXPECT_EQUAL(expect, nested3->dump());
}

TEST("require that convenient tensor create can have multiple values on multiple levels") {
    vespalib::string expect("tensor(x{},y[2]):{{x:\"a\",y:0}:1,{x:\"a\",y:1}:2,{x:\"b\",y:0}:3,{x:\"b\",y:1}:4}");
    auto fun1 = Function::parse("tensor(x{},y[2]):{a:[1,2],b:[3,4]}");
    auto fun2 = Function::parse("tensor(y[2],x{}):[{a:1,b:3},{a:2,b:4}]");
    auto fun3 = Function::parse("tensor(x{},y[2]): { a : [ 1 , 2 ] , b : [ 3 , 4 ] } ");
    auto fun4 = Function::parse("tensor(y[2],x{}): [ { a : 1 , b : 3 } , { a : 2 , b : 4 } ] ");
    EXPECT_EQUAL(expect, fun1->dump());
    EXPECT_EQUAL(expect, fun2->dump());
    EXPECT_EQUAL(expect, fun3->dump());
    EXPECT_EQUAL(expect, fun4->dump());
}

TEST("require that convenient tensor create allows under-specified tensors") {
    auto fun = Function::parse("tensor(x[2],y[2]):[[],[5]]");
    EXPECT_EQUAL("tensor(x[2],y[2]):{{x:1,y:0}:5}", fun->dump());
}

TEST("require that convenient tensor create detects invalid tensor type") {
    TEST_DO(verify_error("tensor(x[,y}):ignored",
                         "[tensor(x[,y})]...[invalid tensor type]...[:ignored]"));
}

TEST("require that convenient tensor create detects too large indexed dimensions") {
    TEST_DO(verify_error("tensor(x[1]):[1,2]",
                         "[tensor(x[1]):[1,]...[dimension too large: 'x']...[2]]"));
}

TEST("require that convenient tensor create detects under-specified cells") {
    TEST_DO(verify_error("tensor(x[1],y[1]):[1]",
                         "[tensor(x[1],y[1]):[]...[expected '[', but got '1']...[1]]"));
}

//-----------------------------------------------------------------------------

TEST("require that tensor peek can be parsed") {
    TEST_DO(verify_parse("t{x:\"1\",y:\"foo\"}", "f(t)(t{x:\"1\",y:\"foo\"})"));
    TEST_DO(verify_parse("t{x:'1',y:'foo'}", "f(t)(t{x:\"1\",y:\"foo\"})"));
    TEST_DO(verify_parse("t{x:1,y:foo}", "f(t)(t{x:\"1\",y:\"foo\"})"));
}

TEST("require that tensor peek can contain expressions") {
    TEST_DO(verify_parse("t{x:(1+2),y:1+2}", "f(t)(t{x:(1+2),y:\"1+2\"})"));
    TEST_DO(verify_parse("t{x:(foo),y:foo}", "f(t,foo)(t{x:(foo),y:\"foo\"})"));
    TEST_DO(verify_parse("t{x:(foo+2),y:foo+2}", "f(t,foo)(t{x:(foo+2),y:\"foo+2\"})"));
}

TEST("require that trivial tensor peek number expressions are converted to verbatim labels") {
    TEST_DO(verify_parse("t{x:(5.7)}", "f(t)(t{x:\"5\"})"));
    TEST_DO(verify_parse("t{x:(5.3)}", "f(t)(t{x:\"5\"})"));
    TEST_DO(verify_parse("t{x:(-5.7)}", "f(t)(t{x:\"-5\"})"));
    TEST_DO(verify_parse("t{x:(-5.3)}", "f(t)(t{x:\"-5\"})"));
}

TEST("require that tensor peek can contain extra whitespace") {
    TEST_DO(verify_parse(" t { x : ( 1 + bar ) , y : ( foo + 2 ) } ",
                         "f(t,bar,foo)(t{x:(1+bar),y:(foo+2)})"));
    TEST_DO(verify_parse(" t { x : \"1 + bar\" , y : \"foo + 2\" } ",
                         "f(t)(t{x:\"1 + bar\",y:\"foo + 2\"})"));
}

TEST("require that empty tensor peek is not allowed") {
    TEST_DO(verify_error("x{}", "[x{}]...[empty peek spec]...[]"));
}

TEST("require that tensor peek empty label is not allowed") {
    TEST_DO(verify_error("x{a:}", "[x{a:]...[missing label]...[}]"));
    TEST_DO(verify_error("x{a:\"\"}", "[x{a:\"\"]...[missing label]...[}]"));
}

//-----------------------------------------------------------------------------

TEST("require that nested tensor lambda using tensor peek can be parsed") {
    vespalib::string expect("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})");
    EXPECT_EQUAL(Function::parse(expect)->dump(), expect);
}

//-----------------------------------------------------------------------------

TEST("require that tensor concat can be parsed") {
    EXPECT_EQUAL("concat(a,b,d)", Function::parse({"a", "b"}, "concat(a,b,d)")->dump());
    EXPECT_EQUAL("concat(a,b,d)", Function::parse({"a", "b"}, " concat ( a , b , d ) ")->dump());
}

//-----------------------------------------------------------------------------

TEST("require that tensor cell cast can be parsed") {
    EXPECT_EQUAL("cell_cast(a,float)", Function::parse({"a"}, "cell_cast(a,float)")->dump());
    EXPECT_EQUAL("cell_cast(a,double)", Function::parse({"a"}, " cell_cast ( a , double ) ")->dump());
}

TEST("require that tensor cell cast must have valid cell type") {
    TEST_DO(verify_error("cell_cast(x,int7)", "[cell_cast(x,int7]...[unknown cell type: 'int7']...[)]"));
}

//-----------------------------------------------------------------------------

struct CheckExpressions : test::EvalSpec::EvalTest {
    bool failed = false;
    size_t seen_cnt = 0;
    virtual void next_expression(const std::vector<vespalib::string> &param_names,
                                 const vespalib::string &expression) override
    {
        auto function = Function::parse(param_names, expression);
        if (function->has_error()) {
            failed = true;
            fprintf(stderr, "parse error: %s\n", function->get_error().c_str());
        }
        ++seen_cnt;
    }
    virtual void handle_case(const std::vector<vespalib::string> &,
                             const std::vector<double> &,
                             const vespalib::string &,
                             double) override {}
};

TEST_FF("require that all conformance test expressions can be parsed",
        CheckExpressions(), test::EvalSpec())
{
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_TRUE(!f1.failed);
    EXPECT_GREATER(f1.seen_cnt, 42u);
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
