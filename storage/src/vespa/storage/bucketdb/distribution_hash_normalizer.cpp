// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distribution_hash_normalizer.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <boost/spirit/include/qi.hpp>
#include <boost/spirit/include/phoenix_core.hpp>
#include <boost/spirit/include/phoenix_object.hpp>
#include <boost/fusion/include/adapt_struct.hpp>
#include <boost/optional.hpp>
#include <boost/variant/recursive_wrapper.hpp>
#include <vector>
#include <algorithm>
#include <iterator>
#include <functional>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".storage.bucketdb.distribution_hash_normalizer");

// TODO
// This code can be removed once we have a model out which ensures consistent
// ordering of nodes in the stor-distribution config.

namespace qi = boost::spirit::qi;
namespace ascii = boost::spirit::ascii;
namespace phoenix = boost::phoenix;

namespace {

struct GroupSet;

using Children = boost::variant<
    std::vector<unsigned int>,
    boost::recursive_wrapper<GroupSet>
>;

struct Group {
    uint16_t index;
    boost::optional<double> capacity;
    Children children;
    ~Group() {}
};

struct GroupSet {
    std::string distribution_spec;
    std::vector<Group> subgroups;
};

} // anon ns

// Fusion adaptations must be in global scope.
BOOST_FUSION_ADAPT_STRUCT(
    ::Group,
    (uint16_t, index)
    (boost::optional<double>, capacity)
    (::Children, children)
)

BOOST_FUSION_ADAPT_STRUCT(
    ::GroupSet,
    (std::string, distribution_spec)
    (std::vector<Group>, subgroups)
)

namespace storage {
namespace {

// Boost.Spirit v2 grammar for parsing the output of lib::Group::getConfigHash.
template <typename Iterator>
struct HashGrammar
    : qi::grammar<Iterator, Group()>
{
    HashGrammar()
        : HashGrammar::base_type(group)
    {
        using qi::uint_;
        using qi::double_;
        using ascii::char_;
        /*
         * This grammar makes the (reasonable) assumption that you can't have
         * empty groups.
         *
         * Quick Spirit PEG DSL syntax primer for any two arbitrary parsers
         * a and b (all subcomponents of parsers are themselves parsers):
         *
         *   'X'     : character literal match parser
         *   a >> b  : a must be followed by b ("a b" in EBNF)
         *   -a      : optional ("a?" in EBNF)
         *   a | b   : a or b must match (same as in EBNF)
         *   +a      : match 1 or more times ("a+" in EBNF)
         *   *a      : kleene star; 0 or more times ("a*" in EBNF)
         *   a - b   : difference; a but not b
         *
         * Please see Boost.Spirit docs on how these map to parser attributes
         * (optional maps to boost::optional of nested attribute, + or kleene
         * star maps to an iterable range (std::vector) of nested attributes,
         * a | b maps to a boost::variant of the attributes of a and b,
         * a >> b maps to a boost::tuple of the attributes and so on; usually
         * fairly intuitive).
         */
        group =
                 '('
              >> uint_
              >> -('c' >> double_)
              >> ( +(';' >> uint_)
                 | subgroups
                 )
              >> ')';

        subgroups = ('d' >> distr_spec >> +group);

        distr_spec = +(char_ - '('); // Match everything until open paren.
    }

    qi::rule<Iterator, Group()> group;
    qi::rule<Iterator, GroupSet()> subgroups;
    qi::rule<Iterator, std::string()> distr_spec;
};

template <typename Range, typename Predicate>
auto ordered_by(const Range& range, Predicate pred) {
    std::vector<typename Range::value_type> copy(
            std::begin(range), std::end(range));
    std::sort(copy.begin(), copy.end(), pred);
    return copy;
}

void emit_normalized_groups(vespalib::asciistream& out, const Group& g);

struct InOrderGroupVisitor : boost::static_visitor<void> {
    vespalib::asciistream& _out;
    InOrderGroupVisitor(vespalib::asciistream& out)
        : _out(out)
    {
    }

    void operator()(const std::vector<unsigned int>& nodes) const {
        for (uint16_t node : ordered_by(nodes, std::less<void>())) {
            _out << ';' << node;
        }
    }

    void operator()(const GroupSet& gs) const {
        _out << 'd' << gs.distribution_spec;
        auto index_less_than = [](auto& lhs, auto& rhs) {
            return lhs.index < rhs.index;
        };
        // Ordering will also copy nested subgroups, but the number of known
        // Vespa installations with nested subgroups is currently somewhere
        // around the high end of zero.
        for (auto& g : ordered_by(gs.subgroups, index_less_than)) {
            emit_normalized_groups(_out, g);
        }
    }
};

void emit_normalized_groups(vespalib::asciistream& out, const Group& g) {
    out << '(' << g.index;
    if (g.capacity) {
        out << 'c' << *g.capacity;
    }
    boost::apply_visitor(InOrderGroupVisitor(out), g.children);
    out << ')';
}

} // anon ns

// We keep the grammar around across multiple normalized() calls because
// constructing the grammar object(s) isn't free.
struct DistributionHashNormalizer::ParserImpl {
    using Iterator = vespalib::string::const_iterator;
    HashGrammar<Iterator> grammar;
};

DistributionHashNormalizer::DistributionHashNormalizer()
    : _impl(std::make_unique<ParserImpl>())
{
}

// Required here because of incomplete ParserImpl in header.
DistributionHashNormalizer::~DistributionHashNormalizer()
{
}

vespalib::string
DistributionHashNormalizer::normalize(vespalib::stringref hash) const
{
    Group root;

    auto iter = hash.begin();
    const bool ok = qi::parse(iter, hash.end(), _impl->grammar, root);
    if (!ok || iter != hash.end()) {
        vespalib::string hash_str = hash; // stringref might not be zero-term'd.
        LOGBT(warning, hash_str.c_str(),
              "Unable to parse compact distribution config "
              "representation: '%s'",
              hash_str.c_str());
        return hash; // Fallback to input on parse failure.
    }

    vespalib::asciistream out;
    emit_normalized_groups(out, root);

    return out.str();
}

} // storage

