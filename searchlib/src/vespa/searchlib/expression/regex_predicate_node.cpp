// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "regex_predicate_node.h"
#include "resultnode.h"
#include "resultvector.h"

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Regex;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, RegexPredicateNode, FilterPredicateNode);

void RegexPredicateNode::RE::compile() {
    regex = Regex::from_pattern(pattern, Regex::Options::None);
}

bool RegexPredicateNode::allow(DocId docId, HitRank rank) {
    bool isMatch = false;
    if (_argument.getRoot()) {
        _argument.execute(docId, rank);
        const ResultNode* result = _argument.getResult();
        if (result->inherits(ResultNodeVector::classId)) {
            const auto * rv = static_cast<const ResultNodeVector *>(result);
            for (size_t i = 0; i < rv->size(); i++) {
                HoldString tmp(*rv, i);
                isMatch = _re.regex.full_match(tmp);
                fprintf(stderr, "RegexPredicateNode check[%zd] match '%s' [%zd]\n",
                        i, tmp.data(), tmp.size());
                if (isMatch) break;
            }
        } else {
            HoldString tmp(*result);
            isMatch = _re.regex.full_match(tmp);
            fprintf(stderr, "RegexPredicateNode check match '%s' [%zd]\n", tmp.data(), tmp.size());
        }

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
