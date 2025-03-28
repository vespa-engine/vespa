// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cassert>
#include <initializer_list>
#include <string>
#include <vector>

namespace vespalib::eval::test {

/**
 * A collection of expressions with parameter bindings and their
 * expected evaluation results. This is intended as the basis for
 * conformance testing of evaluation engines.
 **/
class EvalSpec
{
private:
    typedef double (*fun_1_ref)(double);
    typedef double (*fun_2_ref)(double, double);

    struct Expression {
        struct Case {
            std::vector<double> param_values;
            double expected_result;
            Case(std::initializer_list<double> param_values_in, double expected_result_in)
                : param_values(param_values_in), expected_result(expected_result_in) {}
        };
        std::vector<std::string> param_names;
        std::string expression;
        std::vector<Case> cases;
        Expression(std::initializer_list<std::string> param_names_in, std::string expression_in)
            : param_names(param_names_in), expression(expression_in) {}
        ~Expression();

        Expression &add_case(std::initializer_list<double> param_values, double expected_result);
        Expression &add_cases(std::initializer_list<double> a_values, fun_1_ref fun);
        Expression &add_cases(std::initializer_list<double> a_values, std::initializer_list<double> b_values, fun_2_ref fun);
    };
    std::vector<Expression> expressions;

    Expression &add_expression(std::initializer_list<std::string> param_names, std::string expression) {
        expressions.emplace_back(param_names, expression);
        return expressions.back();
    }

    struct ParamSpec {
        std::string name;
        double min;
        double max;
        std::vector<double> expand(size_t inner_samples) const {
            std::vector<double> ret;
            ret.push_back(min);
            if (max == min) {
                return ret;
            }
            ret.push_back(max);
            if ((min < 0.0) && (max > 0.0)) {
                ret.push_back(0.0);
            }
            double delta = (max - min) / (inner_samples + 1);
            for(size_t i = 0; i < inner_samples; ++i) {
                double x = min + (delta * (i + 1));
                if (x != 0.0) {
                    ret.push_back(x);
                }
            }
            return ret;
        }
    };

    void add_rule(const ParamSpec &a_spec, const std::string &expression, fun_1_ref ref);

    void add_rule(const ParamSpec &a_spec, const ParamSpec &b_spec, const std::string &expression, fun_2_ref ref);

public:
    struct EvalTest {
        static std::string as_string(const std::vector<std::string> &param_names,
                                          const std::vector<double> &param_values,
                                          const std::string &expression);
        bool is_same(double expected, double actual);
        virtual void next_expression(const std::vector<std::string> &param_names,
                                     const std::string &expression) = 0;
        virtual void handle_case(const std::vector<std::string> &param_names,
                                 const std::vector<double> &param_values,
                                 const std::string &expression,
                                 double expected_result) = 0;
        virtual ~EvalTest() = default;
    };
    //-------------------------------------------------------------------------
    void add_terminal_cases();         // a, 1.0
    void add_arithmetic_cases();       // a + b, a ^ b
    void add_function_call_cases();    // cos(a), max(a, b)
    void add_tensor_operation_cases(); // map(a,f(x)(sin(x)))
    void add_comparison_cases();       // a < b, c != d
    void add_set_membership_cases();   // a in [x, y, z]
    void add_boolean_cases();          // 1.0 && 0.0
    void add_if_cases();               // if (a < b, a, b)
    void add_complex_cases();          // ...
    //-------------------------------------------------------------------------
    void add_all_cases() {
        add_terminal_cases();
        add_arithmetic_cases();
        add_function_call_cases();
        add_tensor_operation_cases();
        add_comparison_cases();
        add_set_membership_cases();
        add_boolean_cases();
        add_if_cases();
        add_complex_cases();
    }
    //-------------------------------------------------------------------------
    void each_case(EvalTest &test) const {
        for (const Expression &expr: expressions) {
            test.next_expression(expr.param_names, expr.expression);
            for (const Expression::Case &expr_case: expr.cases) {
                test.handle_case(expr.param_names, expr_case.param_values, expr.expression,
                                 expr_case.expected_result);
            }
        }
    }
};

} // namespace
