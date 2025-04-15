// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "regex_predicate_node.h"
#include "resultnode.h"

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Regex;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, RegexPredicateNode, FilterPredicateNode);

void RegexPredicateNode::RE::compile() {
    regex = Regex::from_pattern(pattern, Regex::Options::None);
}

bool RegexPredicateNode::allow(DocId docId, HitRank rank) {
    bool isMatch = true;
    if (_argument.getRoot()) {
        _argument.execute(docId, rank);
        const ResultNode* result = _argument.getResult();
        char buf[32];
        auto tmp = result->getString({buf, sizeof(buf)});
        isMatch = _re.regex.full_match({tmp.c_str(), tmp.size()});
        fprintf(stderr, "RegexPredicateNode check match '%s' [%zd]\n", tmp.c_str(), tmp.size());
    }
    return isMatch;
}

RegexPredicateNode::RegexPredicateNode() noexcept : _re(), _argument() {}

RegexPredicateNode::~RegexPredicateNode() = default;

Serializer& RegexPredicateNode::onSerialize(Serializer& os) const { return os << _re.pattern << _argument; }

Deserializer& RegexPredicateNode::onDeserialize(Deserializer& is) {
    is >> _re.pattern;
    _re.compile();
    is >> _argument;
    fprintf(stderr, "RegexPredicateNode pattern='%s' arg=%p\n", _re.pattern.c_str(), _argument.getRoot());
    return is;
}

void RegexPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "regexp", _re.pattern);
    visit(visitor, "argument", _argument);
}

void RegexPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                       vespalib::ObjectOperation& operation) {
    _argument.select(predicate, operation);
}

} // namespace search::expression
