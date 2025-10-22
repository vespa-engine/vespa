# Streaming Query Nodes Architecture

## Overview

This document describes the streaming query node classes and their relationship to the tree-based query nodes.

## Architecture Design

The streaming query nodes (`search::streaming`) are **architecturally independent** from the tree query nodes (`search::query::tree`). They serve different purposes:

- **Tree nodes** (`search::query::tree`): Used for query parsing, rewriting, and blueprint creation
- **Streaming nodes** (`search::streaming`): Used for actual streaming search execution

### Key Architectural Difference

**Streaming nodes do NOT inherit from tree nodes.** This means:

- Tree nodes use `QueryNodeMixin<T, Base>` with CRTP pattern
- Streaming nodes inherit from `streaming::QueryNode`, `streaming::QueryTerm`, or `streaming::MultiTerm`
- The `static_cast` in `search::query::CustomTypeVisitor` **will not work** with streaming nodes

## Node Type Mapping

The following table shows the correspondence between tree and streaming node types:

| Tree Node Type               | Streaming Node Type                   | Base Class        |
|------------------------------|---------------------------------------|-------------------|
| `query::And`                 | `streaming::AndQueryNode`             | `QueryConnector`  |
| `query::AndNot`              | `streaming::AndNotQueryNode`          | `QueryConnector`  |
| `query::Or`                  | `streaming::OrQueryNode`              | `QueryConnector`  |
| `query::Rank`                | `streaming::RankWithQueryNode`        | `QueryConnector`  |
| `query::WeakAnd`             | `streaming::WeakAndQueryNode`         | `OrQueryNode`     |
| `query::Near`                | `streaming::NearQueryNode`            | `AndQueryNode`    |
| `query::ONear`               | `streaming::ONearQueryNode`           | `NearQueryNode`   |
| `query::Phrase`              | `streaming::PhraseQueryNode`          | `MultiTerm`       |
| `query::SameElement`         | `streaming::SameElementQueryNode`     | `QueryNode`       |
| `query::Equiv`               | `streaming::EquivQueryNode`           | `MultiTerm`       |
| `query::NumberTerm`          | `streaming::NumberTerm`               | `QueryTerm`       |
| `query::StringTerm`          | `streaming::StringTerm`               | `QueryTerm`       |
| `query::PrefixTerm`          | `streaming::PrefixTerm`               | `QueryTerm`       |
| `query::SubstringTerm`       | `streaming::SubstringTerm`            | `QueryTerm`       |
| `query::SuffixTerm`          | `streaming::SuffixTerm`               | `QueryTerm`       |
| `query::LocationTerm`        | `streaming::LocationTerm`             | `QueryTerm`       |
| `query::RangeTerm`           | `streaming::RangeTerm`                | `QueryTerm`       |
| `query::RegExpTerm`          | `streaming::RegexpTerm`               | `QueryTerm`       |
| `query::FuzzyTerm`           | `streaming::FuzzyTerm`                | `QueryTerm`       |
| `query::WeightedSetTerm`     | `streaming::WeightedSetTerm`          | `MultiTerm`       |
| `query::DotProduct`          | `streaming::DotProductTerm`           | `MultiTerm`       |
| `query::WandTerm`            | `streaming::WandTerm`                 | `DotProductTerm`  |
| `query::InTerm`              | `streaming::InTerm`                   | `MultiTerm`       |
| `query::WordAlternatives`    | `streaming::WordAlternatives`         | `MultiTerm`       |
| `query::PredicateQuery`      | `streaming::PredicateQuery`           | `QueryTerm`       |
| `query::NearestNeighborTerm` | `streaming::NearestNeighborQueryNode` | `QueryNode`       |
| `query::TrueQueryNode`       | `streaming::TrueNode`                 | `QueryConnector`  |
| `query::FalseQueryNode`      | `streaming::FalseNode`                | `QueryConnector`  |

## Visitor Pattern

### For Tree Nodes

Use `search::query::QueryVisitor` or `search::query::CustomTypeVisitor<NodeTypes>`:

```cpp
#include <vespa/searchlib/query/tree/customtypevisitor.h>

struct MyNodeTypes {
    using StringTerm = MyCustomStringTerm;  // Must inherit from query::StringTerm
    // ... other types
};

class MyVisitor : public query::CustomTypeVisitor<MyNodeTypes> {
    void visit(MyCustomStringTerm& term) override { /* ... */ }
};
```

### For Streaming Nodes

There are three visitor options depending on your needs:

#### 1. QueryVisitor - Full Control

Use `streaming::QueryVisitor` when you need to handle both connector and term nodes:

```cpp
#include <vespa/searchlib/query/streaming/query_visitor.h>

class MyStreamingVisitor : public streaming::QueryVisitor {
    void visit(streaming::StringTerm& term) override {
        // Handle string term
    }

    void visit(streaming::AndQueryNode& node) override {
        // Handle AND node - must manually traverse children
        for (auto& child : node.getChildren()) {
            child->accept(*this);
        }
    }

    // Implement all other visit methods...
};
```

#### 2. TermVisitor - Focus on Terms Only

Use `streaming::TermVisitor` when you only care about visiting term nodes (connector nodes are automatically traversed):

```cpp
#include <vespa/searchlib/query/streaming/term_visitor.h>

class MyTermVisitor : public streaming::TermVisitor {
protected:
    void visit(streaming::StringTerm& term) override {
        // Handle string term
    }

    void visit(streaming::PrefixTerm& term) override {
        // Handle prefix term
    }

    // Implement other term visit methods...
    // Connector nodes (AND, OR, etc.) are automatically traversed
};
```

#### 3. TemplateTermVisitor - Handle All Terms the Same Way

Use `streaming::TemplateTermVisitor` when you want to handle all term types uniformly:

```cpp
#include <vespa/searchlib/query/streaming/template_term_visitor.h>

class MyCollector : public streaming::TemplateTermVisitor<MyCollector> {
public:
    std::vector<std::string> terms;

    template <class TermType>
    void visitTerm(TermType& term) {
        terms.push_back(term.getTermString());
    }
};
```

## Files Added

### Term Node Classes
- `number_term.{h,cpp}` - Numeric term search
- `string_term.{h,cpp}` - Exact string term search
- `prefix_term.{h,cpp}` - Prefix matching
- `substring_term.{h,cpp}` - Substring matching
- `suffix_term.{h,cpp}` - Suffix matching
- `location_term.{h,cpp}` - Geographic location search
- `range_term.{h,cpp}` - Numeric range search
- `predicate_query.{h,cpp}` - Predicate query (limited streaming support)

### Multi-Term Node Classes
- `word_alternatives.{h,cpp}` - Alternative word forms

### Intermediate Node Classes
- `weak_and_query_node.{h,cpp}` - Weak AND operator

### Visitor Support Files
- `nodetypes.h` - Type mapping traits for documentation
- `query_visitor.{h,cpp}` - Base visitor pattern for streaming nodes
- `term_visitor.h` - Visitor with automatic connector traversal
- `template_term_visitor.h` - Template-based visitor using CRTP
- `term_visitor_example.h` - Example implementations of TermVisitor

## Usage Notes

1. **Streaming nodes do not inherit from tree nodes** - they are architecturally independent

2. **Choose the right visitor for your use case**:
   - Use `QueryVisitor` when you need full control over traversal
   - Use `TermVisitor` when you only care about term nodes
   - Use `TemplateTermVisitor` when all terms can be handled uniformly

3. **The generic QueryTerm class** handles multiple term types via the `TermType` enum, so many use cases don't need the specific subclasses

4. **Subclass motivation**: The specific term classes were added to provide:
   - Type-safe visitor pattern
   - API completeness to match tree nodes
   - Future extensibility for streaming-specific behavior

## See Also

- `search/query/tree/customtypevisitor.h` - Tree node visitor with custom types
- `search/query/streaming/query_visitor.h` - Base streaming node visitor
- `search/query/streaming/term_visitor.h` - Streaming term visitor with auto-traversal
- `search/query/streaming/template_term_visitor.h` - Template-based streaming visitor
- `search/query/streaming/queryterm.h` - Base streaming term class
- `search/query/streaming/query.h` - Streaming query node base classes
