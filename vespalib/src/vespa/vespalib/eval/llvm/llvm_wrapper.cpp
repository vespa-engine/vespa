// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cmath>
#include "llvm_wrapper.h"
#include <vespa/vespalib/eval/node_visitor.h>
#include <vespa/vespalib/eval/node_traverser.h>
#include <llvm/Analysis/Verifier.h>
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/Intrinsics.h>
#include <llvm/ExecutionEngine/ExecutionEngine.h>
#include <llvm/Analysis/Passes.h>
#include <llvm/IR/DataLayout.h>
#include <llvm/Transforms/Scalar.h>
#include <llvm/LinkAllPasses.h>
#include <llvm/Transforms/IPO/PassManagerBuilder.h>
#include <vespa/vespalib/eval/check_type.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/approx.h>

double vespalib_eval_ldexp(double a, double b) { return std::ldexp(a, b); }
double vespalib_eval_min(double a, double b) { return std::min(a, b); }
double vespalib_eval_max(double a, double b) { return std::max(a, b); }
double vespalib_eval_isnan(double a) { return (std::isnan(a) ? 1.0 : 0.0); }
double vespalib_eval_approx(double a, double b) { return (vespalib::approx_equal(a, b) ? 1.0 : 0.0); }
double vespalib_eval_relu(double a) { return std::max(a, 0.0); }
double vespalib_eval_sigmoid(double a) { return 1.0 / (1.0 + std::exp(-1.0 * a)); }

namespace vespalib {
namespace eval {

using namespace nodes;

namespace {

struct SetMemberHash : PluginState {
    vespalib::hash_set<double> members;
    explicit SetMemberHash(const Array &array) : members(array.size() * 3) {
        for (size_t i = 0; i < array.size(); ++i) {
            members.insert(array.get(i).get_const_value());
        }
    }
    static bool check_membership(const PluginState *state, double value) {
        const SetMemberHash &my_state = *((const SetMemberHash *)state);
        return (my_state.members.find(value) != my_state.members.end());
    }
};

struct FunctionBuilder : public NodeVisitor, public NodeTraverser {

    llvm::ExecutionEngine    &engine;
    llvm::LLVMContext        &context;
    llvm::Module             &module;
    llvm::IRBuilder<>         builder;
    std::vector<llvm::Value*> params;
    std::vector<llvm::Value*> values;
    std::vector<llvm::Value*> let_values;
    llvm::Function           *function;
    bool                      use_array;
    bool                      inside_forest;
    const Node               *forest_end;
    const gbdt::Optimize::Chain &forest_optimizers;
    std::vector<gbdt::Forest::UP> &forests;
    std::vector<PluginState::UP> &plugin_state;

    FunctionBuilder(llvm::ExecutionEngine &engine_in,
                    llvm::LLVMContext &context_in,
                    llvm::Module &module_in,
                    const vespalib::string &name_in,
                    size_t num_params_in,
                    bool use_array_in,
                    const gbdt::Optimize::Chain &forest_optimizers_in,
                    std::vector<gbdt::Forest::UP> &forests_out,
                    std::vector<PluginState::UP> &plugin_state_out)
        : engine(engine_in),
          context(context_in),
          module(module_in),
          builder(context),
          params(),
          values(),
          let_values(),
          function(nullptr),
          use_array(use_array_in),
          inside_forest(false),
          forest_end(nullptr),
          forest_optimizers(forest_optimizers_in),
          forests(forests_out),
          plugin_state(plugin_state_out)
    {
        std::vector<llvm::Type*> param_types;
        if (use_array_in) {
            param_types.push_back(builder.getDoubleTy()->getPointerTo());
        } else {
            param_types.resize(num_params_in, builder.getDoubleTy());
        }
        llvm::FunctionType *function_type = llvm::FunctionType::get(builder.getDoubleTy(), param_types, false);
        function = llvm::Function::Create(function_type, llvm::Function::ExternalLinkage, name_in.c_str(), &module);
        function->addFnAttr(llvm::Attribute::AttrKind::NoInline);
        llvm::BasicBlock *block = llvm::BasicBlock::Create(context, "entry", function);
        builder.SetInsertPoint(block);
        for (llvm::Function::arg_iterator itr = function->arg_begin(); itr != function->arg_end(); ++itr) {
            params.push_back(itr);
        }
    }

    //-------------------------------------------------------------------------

    llvm::Value *get_param(size_t idx) {
        if (!use_array) {
            assert(idx < params.size());
            return params[idx];
        }
        assert(params.size() == 1);
        llvm::Value *param_array = params[0];
        llvm::Value *addr = builder.CreateGEP(param_array, builder.getInt64(idx));
        return builder.CreateLoad(addr);
    }

    //-------------------------------------------------------------------------

    void push(llvm::Value *value) {
        values.push_back(value);
    }

    void discard() {
        assert(!values.empty());
        values.pop_back();
    }

    llvm::Value *pop_bool() {
        assert(!values.empty());
        llvm::Value *value = values.back();
        values.pop_back();
        if (value->getType()->isIntegerTy(1)) {
            return value;
        }
        assert(value->getType()->isDoubleTy());
        return builder.CreateFCmpUNE(value, llvm::ConstantFP::get(context, llvm::APFloat(0.0)), "as_bool");
    }

    llvm::Value *pop_double() {
        assert(!values.empty());
        llvm::Value *value = values.back();
        values.pop_back();
        if (value->getType()->isDoubleTy()) {
            return value;
        }
        assert(value->getType()->isIntegerTy(1));
        return builder.CreateUIToFP(value, builder.getDoubleTy(), "as_double");
    }

    //-------------------------------------------------------------------------

    bool try_optimize_forest(const Node &item) {
        auto trees = gbdt::extract_trees(item);
        gbdt::ForestStats stats(trees);
        auto optimize_result = gbdt::Optimize::apply_chain(forest_optimizers, stats, trees);
        if (!optimize_result.valid()) {
            return false;
        }
        forests.push_back(std::move(optimize_result.forest));
        void *eval_ptr = (void *) optimize_result.eval;
        gbdt::Forest *forest = forests.back().get();
        std::vector<llvm::Type*> param_types;
        param_types.push_back(builder.getVoidTy()->getPointerTo());
        param_types.push_back(builder.getDoubleTy()->getPointerTo());
        llvm::FunctionType *function_type = llvm::FunctionType::get(builder.getDoubleTy(), param_types, false);
        llvm::PointerType *function_pointer_type = llvm::PointerType::get(function_type, 0);
        llvm::Value *eval_fun = builder.CreateIntToPtr(builder.getInt64((uint64_t)eval_ptr), function_pointer_type, "inject_eval");
        llvm::Value *ctx = builder.CreateIntToPtr(builder.getInt64((uint64_t)forest), builder.getVoidTy()->getPointerTo(), "inject_ctx");
        push(builder.CreateCall2(eval_fun, ctx, function->arg_begin(), "call_eval"));
        return true;
    }

    //-------------------------------------------------------------------------

    bool open(const Node &node) {
        if (node.is_const()) {
            push_double(node.get_const_value());
            return false;
        }
        if (!inside_forest && use_array && node.is_forest()) {
            if (try_optimize_forest(node)) {
                return false;
            }
            inside_forest = true;
            forest_end = &node;
        }
        if (check_type<Array, If, Let, In>(node)) {
            node.accept(*this);
            return false;
        }
        return true;
    }

    void close(const Node &node) {
        node.accept(*this);
        if (inside_forest && (forest_end == &node)) {
            inside_forest = false;
            forest_end = nullptr;
        }
    }

    //-------------------------------------------------------------------------

    void build_root(const Node &node) {
        node.traverse(*this);
    }

    void build_forest_fragment(const std::vector<const Node *> &trees) {
        inside_forest = true;
        assert(!trees.empty());
        llvm::Value *sum = nullptr;
        for (auto tree: trees) {
            tree->traverse(*this);
            llvm::Value *tree_value = pop_double();
            sum = (sum)
                  ? builder.CreateFAdd(sum, tree_value, "add_tree")
                  : tree_value;
        }
        push(sum);
        inside_forest = false;
    }

    void *compile() {
        builder.CreateRet(pop_double());
        assert(values.empty());
        llvm::verifyFunction(*function);
        return engine.getPointerToFunction(function);
    }

    //-------------------------------------------------------------------------

    void push_double(double value) {
        push(llvm::ConstantFP::get(builder.getDoubleTy(), value));
    }

    void make_error(size_t num_children) {
        for (size_t i = 0; i < num_children; ++i) {
            discard();
        }
        push_double(error_value);
    }

    void make_call_1(llvm::Function *fun) {
        if (fun == nullptr || fun->arg_size() != 1) {
            return make_error(1);
        }
        llvm::Value *a = pop_double();
        push(builder.CreateCall(fun, a));
    }
    void make_call_1(const llvm::Intrinsic::ID &id) {
        make_call_1(llvm::Intrinsic::getDeclaration(&module, id, builder.getDoubleTy()));
    }
    void make_call_1(const char *name) {
        make_call_1(dynamic_cast<llvm::Function*>(module.getOrInsertFunction(name,
                                builder.getDoubleTy(),
                                builder.getDoubleTy(), nullptr)));
    }

    void make_call_2(llvm::Function *fun) {
        if (fun == nullptr || fun->arg_size() != 2) {
            return make_error(2);
        }
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateCall2(fun, a, b));
    }
    void make_call_2(const llvm::Intrinsic::ID &id) {
        make_call_2(llvm::Intrinsic::getDeclaration(&module, id, builder.getDoubleTy()));
    }
    void make_call_2(const char *name) {
        make_call_2(dynamic_cast<llvm::Function*>(module.getOrInsertFunction(name,
                                builder.getDoubleTy(),
                                builder.getDoubleTy(),
                                builder.getDoubleTy(), nullptr)));
    }

    //-------------------------------------------------------------------------

    // basic nodes

    virtual void visit(const Number &item) {
        push_double(item.value());
    }
    virtual void visit(const Symbol &item) {
        if (item.id() >= 0) {
            push(get_param(item.id()));
        } else {
            int let_offset = -(item.id() + 1);
            assert(size_t(let_offset) < let_values.size());
            push(let_values[let_offset]);
        }
    }
    virtual void visit(const String &item) {
        push_double(item.hash());
    }
    virtual void visit(const Array &item) {
        // NB: visit not open
        push_double(item.size());
    }
    virtual void visit(const Neg &) {
        llvm::Value *child = pop_double();
        push(builder.CreateFNeg(child, "neg_res"));
    }
    virtual void visit(const Not &) {
        llvm::Value *child = pop_bool();
        push(builder.CreateNot(child, "not_res"));
    }
    virtual void visit(const If &item) {
        // NB: visit not open
        llvm::BasicBlock *true_block = llvm::BasicBlock::Create(context, "true_block", function);
        llvm::BasicBlock *false_block = llvm::BasicBlock::Create(context, "false_block", function);
        llvm::BasicBlock *merge_block = llvm::BasicBlock::Create(context, "merge_block", function);
        item.cond().traverse(*this); // NB: recursion
        llvm::Value *cond = pop_bool();
        builder.CreateCondBr(cond, true_block, false_block);
        // true block
        builder.SetInsertPoint(true_block);
        item.true_expr().traverse(*this); // NB: recursion
        llvm::Value *true_res = pop_double();
        llvm::BasicBlock *true_end = builder.GetInsertBlock();
        builder.CreateBr(merge_block);
        // false block
        builder.SetInsertPoint(false_block);
        item.false_expr().traverse(*this); // NB: recursion
        llvm::Value *false_res = pop_double();
        llvm::BasicBlock *false_end = builder.GetInsertBlock();
        builder.CreateBr(merge_block);
        // merge block
        builder.SetInsertPoint(merge_block);
        llvm::PHINode *phi = builder.CreatePHI(builder.getDoubleTy(), 2, "if_res");
        phi->addIncoming(true_res, true_end);
        phi->addIncoming(false_res, false_end);
        push(phi);
    }
    virtual void visit(const Let &item) {
        // NB: visit not open
        item.value().traverse(*this); // NB: recursion
        let_values.push_back(pop_double());
        item.expr().traverse(*this); // NB: recursion
        let_values.pop_back();
    }
    virtual void visit(const Error &) {
        make_error(0);
    }

    // tensor nodes (not supported in compiled expressions)

    virtual void visit(const TensorSum &node) {
        make_error(node.num_children());
    }
    virtual void visit(const TensorMap &node) {
        make_error(node.num_children());
    }
    virtual void visit(const TensorJoin &node) {
        make_error(node.num_children());
    }
    virtual void visit(const TensorReduce &node) {
        make_error(node.num_children());
    }
    virtual void visit(const TensorRename &node) {
        make_error(node.num_children());
    }
    virtual void visit(const TensorLambda &node) {
        make_error(node.num_children());
    }
    virtual void visit(const TensorConcat &node) {
        make_error(node.num_children());
    }

    // operator nodes

    virtual void visit(const Add &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFAdd(a, b, "add_res"));
    }
    virtual void visit(const Sub &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFSub(a, b, "sub_res"));
    }
    virtual void visit(const Mul &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFMul(a, b, "mul_res"));
    }
    virtual void visit(const Div &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFDiv(a, b, "div_res"));
    }
    virtual void visit(const Pow &) {
        make_call_2(llvm::Intrinsic::pow);
    }
    virtual void visit(const Equal &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFCmpOEQ(a, b, "cmp_eq_res"));
    }
    virtual void visit(const NotEqual &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFCmpUNE(a, b, "cmp_ne_res"));
    }
    virtual void visit(const Approx &) {
        make_call_2("vespalib_eval_approx");
    }
    virtual void visit(const Less &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFCmpOLT(a, b, "cmp_lt_res"));
    }
    virtual void visit(const LessEqual &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFCmpOLE(a, b, "cmp_le_res"));
    }
    virtual void visit(const Greater &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFCmpOGT(a, b, "cmp_gt_res"));
    }
    virtual void visit(const GreaterEqual &) {
        llvm::Value *b = pop_double();
        llvm::Value *a = pop_double();
        push(builder.CreateFCmpOGE(a, b, "cmp_ge_res"));
    }
    virtual void visit(const In &item) {
        // NB: visit not open
        item.lhs().traverse(*this); // NB: recursion
        llvm::Value *lhs = pop_double();
        auto array = as<Array>(item.rhs());
        if (array) {
            if (array->is_const() && array->size() > 8) {
                // build call to hash lookup
                plugin_state.emplace_back(new SetMemberHash(*array));
                void *call_ptr = (void *) SetMemberHash::check_membership;
                PluginState *state = plugin_state.back().get();
                std::vector<llvm::Type*> param_types;
                param_types.push_back(builder.getVoidTy()->getPointerTo());
                param_types.push_back(builder.getDoubleTy());
                llvm::FunctionType *function_type = llvm::FunctionType::get(builder.getInt1Ty(), param_types, false);
                llvm::PointerType *function_pointer_type = llvm::PointerType::get(function_type, 0);
                llvm::Value *call_fun = builder.CreateIntToPtr(builder.getInt64((uint64_t)call_ptr), function_pointer_type, "inject_call_addr");
                llvm::Value *ctx = builder.CreateIntToPtr(builder.getInt64((uint64_t)state), builder.getVoidTy()->getPointerTo(), "inject_ctx");
                push(builder.CreateCall2(call_fun, ctx, lhs, "call_check_membership"));
            } else {
                // build explicit code to check all set members
                llvm::Value *found = builder.getFalse();
                for (size_t i = 0; i < array->size(); ++i) {
                    array->get(i).traverse(*this); // NB: recursion
                    llvm::Value *elem = pop_double();
                    llvm::Value *elem_eq = builder.CreateFCmpOEQ(lhs, elem, "elem_eq");
                    found = builder.CreateOr(found, elem_eq, "found");
                }
                push(found);
            }
        } else {
            item.rhs().traverse(*this); // NB: recursion
            llvm::Value *rhs = pop_double();
            push(builder.CreateFCmpOEQ(lhs, rhs, "rhs_eq"));
        }
    }
    virtual void visit(const And &) {
        llvm::Value *b = pop_bool();
        llvm::Value *a = pop_bool();
        push(builder.CreateAnd(a, b, "and_res"));
    }
    virtual void visit(const Or &) {
        llvm::Value *b = pop_bool();
        llvm::Value *a = pop_bool();
        push(builder.CreateOr(a, b, "or_res"));
    }

    // call nodes

    virtual void visit(const Cos &) {
        make_call_1(llvm::Intrinsic::cos);
    }
    virtual void visit(const Sin &) {
        make_call_1(llvm::Intrinsic::sin);
    }
    virtual void visit(const Tan &) {
        make_call_1("tan");
    }
    virtual void visit(const Cosh &) {
        make_call_1("cosh");
    }
    virtual void visit(const Sinh &) {
        make_call_1("sinh");
    }
    virtual void visit(const Tanh &) {
        make_call_1("tanh");
    }
    virtual void visit(const Acos &) {
        make_call_1("acos");
    }
    virtual void visit(const Asin &) {
        make_call_1("asin");
    }
    virtual void visit(const Atan &) {
        make_call_1("atan");
    }
    virtual void visit(const Exp &) {
        make_call_1(llvm::Intrinsic::exp);
    }
    virtual void visit(const Log10 &) {
        make_call_1(llvm::Intrinsic::log10);
    }
    virtual void visit(const Log &) {
        make_call_1(llvm::Intrinsic::log);
    }
    virtual void visit(const Sqrt &) {
        make_call_1(llvm::Intrinsic::sqrt);
    }
    virtual void visit(const Ceil &) {
        make_call_1(llvm::Intrinsic::ceil);
    }
    virtual void visit(const Fabs &) {
        make_call_1(llvm::Intrinsic::fabs);
    }
    virtual void visit(const Floor &) {
        make_call_1(llvm::Intrinsic::floor);
    }
    virtual void visit(const Atan2 &) {
        make_call_2("atan2");
    }
    virtual void visit(const Ldexp &) {
        make_call_2("vespalib_eval_ldexp");
    }
    virtual void visit(const Pow2 &) {
        make_call_2(llvm::Intrinsic::pow);
    }
    virtual void visit(const Fmod &) {
        make_call_2("fmod");
    }
    virtual void visit(const Min &) {
        make_call_2("vespalib_eval_min");
    }
    virtual void visit(const Max &) {
        make_call_2("vespalib_eval_max");
    }
    virtual void visit(const IsNan &) {
        make_call_1("vespalib_eval_isnan");
    }
    virtual void visit(const Relu &) {
        make_call_1("vespalib_eval_relu");
    }
    virtual void visit(const Sigmoid &) {
        make_call_1("vespalib_eval_sigmoid");
    }
};

} // namespace vespalib::eval::<unnamed>

struct InitializeNativeTarget {
    InitializeNativeTarget() {
        LLVMInitializeNativeTarget();
    }
} initialize_native_target;

std::recursive_mutex LLVMWrapper::_global_llvm_lock;

LLVMWrapper::LLVMWrapper()
    : _context(nullptr),
      _module(nullptr),
      _engine(nullptr),
      _num_functions(0),
      _forests(),
      _plugin_state()
{
    std::lock_guard<std::recursive_mutex> guard(_global_llvm_lock);
    _context = new llvm::LLVMContext();
    _module = new llvm::Module("LLVMWrapper", *_context);
    _engine = llvm::EngineBuilder(_module).setOptLevel(llvm::CodeGenOpt::Aggressive).create();
    assert(_engine != nullptr && "llvm jit not available for your platform");
}

LLVMWrapper::LLVMWrapper(LLVMWrapper &&rhs)
    : _context(rhs._context),
      _module(rhs._module),
      _engine(rhs._engine),
      _num_functions(rhs._num_functions),
      _forests(std::move(rhs._forests)),
      _plugin_state(std::move(rhs._plugin_state))
{
    rhs._context = nullptr;
    rhs._module = nullptr;
    rhs._engine = nullptr;
}

void *
LLVMWrapper::compile_function(size_t num_params, bool use_array, const Node &root,
                              const gbdt::Optimize::Chain &forest_optimizers)
{
    std::lock_guard<std::recursive_mutex> guard(_global_llvm_lock);
    FunctionBuilder builder(*_engine, *_context, *_module,
                            vespalib::make_string("f%zu", ++_num_functions),
                            num_params, use_array,
                            forest_optimizers, _forests, _plugin_state);
    builder.build_root(root);
    return builder.compile();
}

void *
LLVMWrapper::compile_forest_fragment(const std::vector<const Node *> &fragment)
{
    std::lock_guard<std::recursive_mutex> guard(_global_llvm_lock);
    FunctionBuilder builder(*_engine, *_context, *_module,
                            vespalib::make_string("f%zu", ++_num_functions),
                            0, true,
                            gbdt::Optimize::none, _forests, _plugin_state);
    builder.build_forest_fragment(fragment);
    return builder.compile();
}

LLVMWrapper::~LLVMWrapper() {
    std::lock_guard<std::recursive_mutex> guard(_global_llvm_lock);
    delete _engine;
    // _module is owned by _engine
    delete _context;
}

} // namespace vespalib::eval
} // namespace vespalib
