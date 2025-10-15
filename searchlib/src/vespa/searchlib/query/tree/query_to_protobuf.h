// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integer_term_vector.h"
#include "intermediatenodes.h"
#include "queryvisitor.h"
#include "string_term_vector.h"
#include "weighted_integer_term_vector.h"
#include "weighted_string_term_vector.h"
#include "termnodes.h"
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/query/numeric_range_spec.h>
#include <vespa/searchlib/engine/search_protocol.pb.h>
#include <cassert>

namespace search::query {

/**
 * Serializes a query tree to a protobuf QueryTree message.
 */
class QueryToProtobuf : private QueryVisitor {
    using ProtoQueryTree = searchlib::searchprotocol::protobuf::QueryTree;
    using ProtoItem = searchlib::searchprotocol::protobuf::QueryTreeItem;
    using ProtoProperties = searchlib::searchprotocol::protobuf::TermItemProperties;

    std::vector<ProtoItem*> _item_stack;

public:
    ProtoQueryTree serialize(const Node &node) {
        ProtoQueryTree tree;
        _item_stack.push_back(tree.mutable_root());
        const_cast<Node &>(node).accept(*this);
        assert(_item_stack.size() == 1);
        return tree;
    }

private:
    void copyTermState(const Term &original, ProtoProperties *props) {
        props->set_index(original.getView());
        if (original.getWeight().percent() != 100) {
            props->set_item_weight(original.getWeight().percent());
        }
        props->set_unique_id(original.getId());
        props->set_do_not_rank(!original.isRanked());
        props->set_do_not_use_position_data(!original.usePositionData());
        props->set_do_not_highlight(false);  // Default value, Term API doesn't expose this yet
        props->set_is_special_token(false);  // Default value, Term API doesn't expose this yet
    }

    ProtoItem* makeChild() {
        auto* parent = _item_stack.back();
        ProtoItem* child = nullptr;

        // Determine which intermediate node type we're adding to
        if (parent->has_item_or()) {
            child = parent->mutable_item_or()->add_children();
        } else if (parent->has_item_and()) {
            child = parent->mutable_item_and()->add_children();
        } else if (parent->has_item_and_not()) {
            child = parent->mutable_item_and_not()->add_children();
        } else if (parent->has_item_rank()) {
            child = parent->mutable_item_rank()->add_children();
        } else if (parent->has_item_near()) {
            child = parent->mutable_item_near()->add_children();
        } else if (parent->has_item_onear()) {
            child = parent->mutable_item_onear()->add_children();
        } else if (parent->has_item_weak_and()) {
            child = parent->mutable_item_weak_and()->add_children();
        } else if (parent->has_item_equiv()) {
            child = parent->mutable_item_equiv()->add_children();
        } else if (parent->has_item_phrase()) {
            child = parent->mutable_item_phrase()->add_children();
        } else if (parent->has_item_same_element()) {
            child = parent->mutable_item_same_element()->add_children();
        }

        return child;
    }

    void visitNodes(const std::vector<Node *> &nodes) {
        for (auto node : nodes) {
            auto* child = makeChild();
            _item_stack.push_back(child);
            node->accept(*this);
            _item_stack.pop_back();
        }
    }

    void visit(And &node) override {
        _item_stack.back()->mutable_item_and();
        visitNodes(node.getChildren());
    }

    void visit(AndNot &node) override {
        _item_stack.back()->mutable_item_and_not();
        visitNodes(node.getChildren());
    }

    void visit(WeakAnd &node) override {
        auto* item = _item_stack.back()->mutable_item_weak_and();
        item->set_index(node.getView());
        item->set_target_num_hits(node.getTargetNumHits());
        visitNodes(node.getChildren());
    }

    void visit(Equiv &node) override {
        auto* item = _item_stack.back()->mutable_item_equiv();
        auto* props = item->mutable_properties();
        props->set_unique_id(node.getId());
        if (node.getWeight().percent() != 100) {
            props->set_item_weight(node.getWeight().percent());
        }
        visitNodes(node.getChildren());
    }

    void visit(Near &node) override {
        auto* item = _item_stack.back()->mutable_item_near();
        item->set_distance(node.getDistance());
        item->set_num_negative_terms(node.num_negative_terms());
        item->set_exclusion_distance(node.exclusion_distance());
        visitNodes(node.getChildren());
    }

    void visit(ONear &node) override {
        auto* item = _item_stack.back()->mutable_item_onear();
        item->set_distance(node.getDistance());
        item->set_num_negative_terms(node.num_negative_terms());
        item->set_exclusion_distance(node.exclusion_distance());
        visitNodes(node.getChildren());
    }

    void visit(Or &node) override {
        _item_stack.back()->mutable_item_or();
        visitNodes(node.getChildren());
    }

    void visit(Phrase &node) override {
        auto* item = _item_stack.back()->mutable_item_phrase();
        copyTermState(node, item->mutable_properties());
        visitNodes(node.getChildren());
    }

    void visit(SameElement &node) override {
        auto* item = _item_stack.back()->mutable_item_same_element();
        copyTermState(node, item->mutable_properties());
        visitNodes(node.getChildren());
    }

    void serializeMultiTerm(const MultiTerm& node,
                           google::protobuf::RepeatedPtrField<searchlib::searchprotocol::protobuf::PureWeightedString>* weighted_strings,
                           google::protobuf::RepeatedPtrField<searchlib::searchprotocol::protobuf::PureWeightedLong>* weighted_longs) {
        uint32_t num_terms = node.getNumTerms();
        switch (node.getType()) {
        case MultiTerm::Type::STRING:
        case MultiTerm::Type::WEIGHTED_STRING:
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = node.getAsString(i);
                auto* item = weighted_strings->Add();
                item->set_value(v.first);
                item->set_weight(v.second.percent());
            }
            break;
        case MultiTerm::Type::INTEGER:
        case MultiTerm::Type::WEIGHTED_INTEGER:
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = node.getAsInteger(i);
                auto* item = weighted_longs->Add();
                item->set_value(v.first);
                item->set_weight(v.second.percent());
            }
            break;
        case MultiTerm::Type::UNKNOWN:
            assert(num_terms == 0);
            break;
        }
    }

    void visit(WeightedSetTerm &node) override {
        bool is_string = (node.getType() == MultiTerm::Type::STRING ||
                         node.getType() == MultiTerm::Type::WEIGHTED_STRING);
        if (is_string) {
            auto* item = _item_stack.back()->mutable_item_weighted_set_of_string();
            copyTermState(node, item->mutable_properties());
            serializeMultiTerm(node, item->mutable_weighted_strings(), nullptr);
        } else {
            auto* item = _item_stack.back()->mutable_item_weighted_set_of_long();
            copyTermState(node, item->mutable_properties());
            serializeMultiTerm(node, nullptr, item->mutable_weighted_longs());
        }
    }

    void visit(DotProduct &node) override {
        bool is_string = (node.getType() == MultiTerm::Type::STRING ||
                         node.getType() == MultiTerm::Type::WEIGHTED_STRING);
        if (is_string) {
            auto* item = _item_stack.back()->mutable_item_dot_product_of_string();
            copyTermState(node, item->mutable_properties());
            serializeMultiTerm(node, item->mutable_weighted_strings(), nullptr);
        } else {
            auto* item = _item_stack.back()->mutable_item_dot_product_of_long();
            copyTermState(node, item->mutable_properties());
            serializeMultiTerm(node, nullptr, item->mutable_weighted_longs());
        }
    }

    void visit(WandTerm &node) override {
        bool is_string = (node.getType() == MultiTerm::Type::STRING ||
                         node.getType() == MultiTerm::Type::WEIGHTED_STRING);
        if (is_string) {
            auto* item = _item_stack.back()->mutable_item_string_wand();
            copyTermState(node, item->mutable_properties());
            item->set_target_num_hits(node.getTargetNumHits());
            item->set_score_threshold(node.getScoreThreshold());
            item->set_threshold_boost_factor(node.getThresholdBoostFactor());
            serializeMultiTerm(node, item->mutable_weighted_strings(), nullptr);
        } else {
            auto* item = _item_stack.back()->mutable_item_long_wand();
            copyTermState(node, item->mutable_properties());
            item->set_target_num_hits(node.getTargetNumHits());
            item->set_score_threshold(node.getScoreThreshold());
            item->set_threshold_boost_factor(node.getThresholdBoostFactor());
            serializeMultiTerm(node, nullptr, item->mutable_weighted_longs());
        }
    }

    void visit(Rank &node) override {
        _item_stack.back()->mutable_item_rank();
        visitNodes(node.getChildren());
    }

    void visit(NumberTerm &node) override {
        const auto& term_str = node.getTerm();
        // Check if the term contains a decimal point to determine if it's floating point
        if (term_str.find('.') != std::string::npos || term_str.find('e') != std::string::npos || term_str.find('E') != std::string::npos) {
            auto* item = _item_stack.back()->mutable_item_floating_point_term();
            copyTermState(node, item->mutable_properties());
            item->set_number(std::stod(term_str));
        } else {
            auto* item = _item_stack.back()->mutable_item_integer_term();
            copyTermState(node, item->mutable_properties());
            item->set_number(std::stoll(term_str));
        }
    }

    void visit(LocationTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_geo_location_term();
        copyTermState(node, item->mutable_properties());

        const auto& loc = node.getTerm();
        // Convert internal coordinates to degrees
        // GeoLocation stores x,y as int32 coordinates in microdegrees (scaled by 1000000)
        constexpr double M = 1000000.0;
        if (loc.has_point) {
            item->set_has_geo_circle(true);
            item->set_latitude(loc.point.y / M);
            item->set_longitude(loc.point.x / M);
            if (loc.has_radius()) {
                item->set_radius(loc.radius / M);
            } else {
                item->set_radius(-1.0);
            }
        }
        if (loc.bounding_box.active()) {
            item->set_has_bounding_box(true);
            item->set_s(loc.bounding_box.y.low / M);
            item->set_w(loc.bounding_box.x.low / M);
            item->set_n(loc.bounding_box.y.high / M);
            item->set_e(loc.bounding_box.x.high / M);
        }
    }

    void visit(PrefixTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_prefix_term();
        copyTermState(node, item->mutable_properties());
        item->set_word(node.getTerm());
    }

    void visit(RangeTerm &node) override {
        // Parse the range string format using NumericRangeSpec
        const auto& range_str = node.getTerm().getRangeString();
        auto spec = NumericRangeSpec::fromString(range_str);

        if (!spec || !spec->valid) {
            return;
        }

        if (spec->valid_integers) {
            auto* item = _item_stack.back()->mutable_item_integer_range_term();
            copyTermState(node, item->mutable_properties());
            item->set_lower_limit(spec->int64_lower_limit);
            item->set_upper_limit(spec->int64_upper_limit);
            item->set_lower_inclusive(spec->lower_inclusive);
            item->set_upper_inclusive(spec->upper_inclusive);

            if (spec->has_range_limit()) {
                item->set_has_range_limit(true);
                item->set_range_limit(spec->rangeLimit);
            }
            if (spec->with_diversity()) {
                item->set_with_diversity(true);
                item->set_diversity_attribute(spec->diversityAttribute);
                item->set_diversity_max_per_group(spec->maxPerGroup);
                if (spec->with_diversity_cutoff()) {
                    item->set_with_diversity_cutoff(true);
                    item->set_diversity_cutoff_groups(spec->diversityCutoffGroups);
                    item->set_diversity_cutoff_strict(spec->diversityCutoffStrict);
                }
            }
        } else {
            auto* item = _item_stack.back()->mutable_item_floating_point_range_term();
            copyTermState(node, item->mutable_properties());
            item->set_lower_limit(spec->fp_lower_limit);
            item->set_upper_limit(spec->fp_upper_limit);
            item->set_lower_inclusive(spec->lower_inclusive);
            item->set_upper_inclusive(spec->upper_inclusive);

            if (spec->has_range_limit()) {
                item->set_has_range_limit(true);
                item->set_range_limit(spec->rangeLimit);
            }
            if (spec->with_diversity()) {
                item->set_with_diversity(true);
                item->set_diversity_attribute(spec->diversityAttribute);
                item->set_diversity_max_per_group(spec->maxPerGroup);
                if (spec->with_diversity_cutoff()) {
                    item->set_with_diversity_cutoff(true);
                    item->set_diversity_cutoff_groups(spec->diversityCutoffGroups);
                    item->set_diversity_cutoff_strict(spec->diversityCutoffStrict);
                }
            }
        }
    }

    void visit(StringTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_word_term();
        copyTermState(node, item->mutable_properties());
        item->set_word(node.getTerm());
    }

    void visit(SubstringTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_substring_term();
        copyTermState(node, item->mutable_properties());
        item->set_word(node.getTerm());
    }

    void visit(SuffixTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_suffix_term();
        copyTermState(node, item->mutable_properties());
        item->set_word(node.getTerm());
    }

    void visit(PredicateQuery &node) override {
        auto* item = _item_stack.back()->mutable_item_predicate_query();
        copyTermState(node, item->mutable_properties());

        const auto& term = node.getTerm();
        for (const auto& feature : term->getFeatures()) {
            auto* f = item->add_features();
            f->set_key(feature.getKey());
            f->set_value(feature.getValue());
            f->set_sub_queries(feature.getSubQueryBitmap());
        }
        for (const auto& range : term->getRangeFeatures()) {
            auto* r = item->add_range_features();
            r->set_key(range.getKey());
            r->set_value(range.getValue());
            r->set_sub_queries(range.getSubQueryBitmap());
        }
    }

    void visit(RegExpTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_regexp();
        copyTermState(node, item->mutable_properties());
        item->set_regexp(node.getTerm());
    }

    void visit(NearestNeighborTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_nearest_neighbor();
        copyTermState(node, item->mutable_properties());
        item->set_query_tensor_name(node.get_query_tensor_name());
        item->set_target_num_hits(node.get_target_num_hits());
        item->set_allow_approximate(node.get_allow_approximate());
        item->set_explore_additional_hits(node.get_explore_additional_hits());
        item->set_distance_threshold(node.get_distance_threshold());
    }

    void visit(TrueQueryNode &) override {
        _item_stack.back()->mutable_item_true();
    }

    void visit(FalseQueryNode &) override {
        _item_stack.back()->mutable_item_false();
    }

    void visit(FuzzyTerm &node) override {
        auto* item = _item_stack.back()->mutable_item_fuzzy();
        copyTermState(node, item->mutable_properties());
        item->set_word(node.getTerm());
        item->set_max_edit_distance(node.max_edit_distance());
        item->set_prefix_lock_length(node.prefix_lock_length());
        item->set_prefix_match(node.prefix_match());
    }

    void visit(InTerm& node) override {
        bool is_string = (node.getType() == MultiTerm::Type::STRING ||
                         node.getType() == MultiTerm::Type::WEIGHTED_STRING);
        if (is_string) {
            auto* item = _item_stack.back()->mutable_item_string_in();
            copyTermState(node, item->mutable_properties());
            for (uint32_t i = 0; i < node.getNumTerms(); i++) {
                auto v = node.getAsString(i);
                item->add_words(v.first);
            }
        } else {
            auto* item = _item_stack.back()->mutable_item_numeric_in();
            copyTermState(node, item->mutable_properties());
            for (uint32_t i = 0; i < node.getNumTerms(); i++) {
                auto v = node.getAsInteger(i);
                item->add_numbers(v.first);
            }
        }
    }

    void visit(WordAlternatives& node) override {
        auto* item = _item_stack.back()->mutable_item_word_alternatives();
        copyTermState(node, item->mutable_properties());
        serializeMultiTerm(node, item->mutable_weighted_strings(), nullptr);
    }
};

}
