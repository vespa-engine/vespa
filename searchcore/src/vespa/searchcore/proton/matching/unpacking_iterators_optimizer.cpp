// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unpacking_iterators_optimizer.h"

#include <vespa/log/log.h>
LOG_SETUP(".matching.unpacking_iterators_optimizer");

#include "querynodes.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/searchlib/query/tree/queryvisitor.h>
#include <vespa/searchlib/query/tree/templatetermvisitor.h>
#include <vespa/searchlib/query/tree/querytreecreator.h>

using namespace search::query;

namespace proton::matching {

namespace {

struct TermExpander : QueryVisitor {
    std::vector<Node::UP> terms;

    template <typename T>
    void expand(T &n) {
        n.set_expensive(true);
        for (Node *child: n.getChildren()) {
            Node::UP node = QueryTreeCreator<ProtonNodeTypes>::replicate(*child);
            if (Term *term = dynamic_cast<Term *>(node.get())) {
                term->setRanked(false);
                term->setPositionData(false);
                terms.push_back(std::move(node));
            } else {
                LOG(error, "Required a search::query::TermNode. Got %s", vespalib::getClassName(*node).c_str());
            }
        }
    }
    void visit(And &) override {}
    void visit(AndNot &) override {}
    void visit(Equiv &) override {}
    void visit(NumberTerm &) override {}
    void visit(LocationTerm &) override {}
    void visit(Near &) override {}
    void visit(ONear &) override {}
    void visit(Or &) override {}
    void visit(Phrase &n) override { expand(n); }
    void visit(SameElement &) override {
        // TODO expand(n) once we figure out to handle artificial terms in matched-elements-only;
    }
    void visit(PrefixTerm &) override {}
    void visit(RangeTerm &) override {}
    void visit(Rank &) override {}
    void visit(StringTerm &) override {}
    void visit(SubstringTerm &) override {}
    void visit(SuffixTerm &) override {}
    void visit(WeakAnd &) override {}
    void visit(WeightedSetTerm &) override {}
    void visit(DotProduct &) override {}
    void visit(WandTerm &) override {}
    void visit(PredicateQuery &) override {}
    void visit(RegExpTerm &) override {}
    void visit(NearestNeighborTerm &) override {}
    void visit(TrueQueryNode &) override {}
    void visit(FalseQueryNode &) override {}
    void visit(FuzzyTerm &) override {}

    void flush(Intermediate &parent) {
        for (Node::UP &term: terms) {
            parent.append(std::move(term));
        }
        terms.clear();
    }
};

struct NodeTraverser : TemplateTermVisitor<NodeTraverser, ProtonNodeTypes>
{
    bool split_unpacking_iterators;

    NodeTraverser(bool split_unpacking_iterators_in)
        : split_unpacking_iterators(split_unpacking_iterators_in) {}
    template <class TermNode> void visitTerm(TermNode &) {}
    void visit(ProtonNodeTypes::And &n) override {
        for (Node *child: n.getChildren()) {
            child->accept(*this);
        }
        if (split_unpacking_iterators) {
            TermExpander expander;
            for (Node *child: n.getChildren()) {
                child->accept(expander);
            }
            expander.flush(n);
        }
    }
};

} // namespace proton::matching::<unnamed>

search::query::Node::UP
UnpackingIteratorsOptimizer::optimize(search::query::Node::UP root,
                                      bool has_white_list,
                                      bool split_unpacking_iterators)
{
    if (split_unpacking_iterators) {
        NodeTraverser traverser(split_unpacking_iterators);
        root->accept(traverser);
    }
    if (has_white_list && split_unpacking_iterators) {
        TermExpander expander;
        root->accept(expander);
        if (!expander.terms.empty()) {
            Intermediate::UP and_node = std::make_unique<ProtonNodeTypes::And>();
            and_node->append(std::move(root));
            expander.flush(*and_node);
            root = std::move(and_node);
        }
    }
    return root;
}

} // namespace proton::matching
