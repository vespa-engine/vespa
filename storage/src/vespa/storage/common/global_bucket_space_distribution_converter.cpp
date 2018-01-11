// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "global_bucket_space_distribution_converter.h"
#include <vespa/config/config.h>
#include <vespa/config/print/asciiconfigwriter.h>
#include <vespa/config/print/asciiconfigreader.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vdslib/distribution/distribution_config_util.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>
#include <map>
#include <memory>

namespace storage {

using DistributionConfig = vespa::config::content::StorDistributionConfig;
using DistributionConfigBuilder = vespa::config::content::StorDistributionConfigBuilder;

namespace {

struct Group {
    uint16_t nested_leaf_count{0};
    std::map<uint16_t, std::unique_ptr<Group>> sub_groups;
};

void set_distribution_invariant_config_fields(DistributionConfigBuilder& builder, const DistributionConfig& source) {
    builder.diskDistribution = source.diskDistribution;
    builder.distributorAutoOwnershipTransferOnWholeGroupDown = true;
    builder.activePerLeafGroup = true;
    // TODO consider how to best support n-of-m replication for global docs
    builder.ensurePrimaryPersisted = true;
    builder.initialRedundancy = 0;
}

const Group& find_non_root_group_by_index(const vespalib::string& index, const Group& root) {
    auto path = lib::DistributionConfigUtil::getGroupPath(index);
    auto* node = &root;
    for (auto idx : path) {
        auto child_iter = node->sub_groups.find(idx);
        assert(child_iter != node->sub_groups.end());
        node = child_iter->second.get();
    }
    return *node;
}

vespalib::string sub_groups_to_partition_spec(const Group& parent) {
    vespalib::asciistream partitions;
    // In case of a flat cluster config, this ends up with a partition spec of '*',
    // which is fine. It basically means "put all replicas in this group", which
    // happens to be exactly what we want.
    for (auto& child : parent.sub_groups) {
        partitions << child.second->nested_leaf_count << '|';
    }
    partitions << '*';
    return partitions.str();
}

bool is_leaf_group(const DistributionConfigBuilder::Group& g) noexcept {
    return !g.nodes.empty();
}

void insert_new_group_into_tree(
        std::unique_ptr<Group> new_group,
        const DistributionConfigBuilder::Group& config_source_group,
        Group& root) {
    const auto path = lib::DistributionConfigUtil::getGroupPath(config_source_group.index);
    assert(!path.empty());

    Group* parent = &root;
    for (size_t i = 0; i < path.size(); ++i) {
        const auto idx = path[i];
        parent->nested_leaf_count += config_source_group.nodes.size(); // Empty if added group is not a leaf.
        auto g_iter = parent->sub_groups.find(idx);
        if (g_iter != parent->sub_groups.end()) {
            assert(i != path.size() - 1);
            parent = g_iter->second.get();
        } else {
            assert(i == path.size() - 1); // Only valid case for last item in path.
            parent->sub_groups.emplace(path.back(), std::move(new_group));
        }
    }
}

void build_transformed_root_group(DistributionConfigBuilder& builder,
                                  const DistributionConfigBuilder::Group& config_source_root,
                                  const Group& parsed_root) {
    DistributionConfigBuilder::Group new_root(config_source_root);
    new_root.partitions = sub_groups_to_partition_spec(parsed_root);
    builder.group.emplace_back(std::move(new_root));
}

void build_transformed_non_root_group(DistributionConfigBuilder& builder,
                                      const DistributionConfigBuilder::Group& config_source_group,
                                      const Group& parsed_root) {
    DistributionConfigBuilder::Group new_group(config_source_group);
    if (!is_leaf_group(config_source_group)) { // Partition specs only apply to inner nodes
        const auto& g = find_non_root_group_by_index(config_source_group.index, parsed_root);
        new_group.partitions = sub_groups_to_partition_spec(g);
    }
    builder.group.emplace_back(std::move(new_group));
}

std::unique_ptr<Group> create_group_tree_from_config(const DistributionConfig& source) {
    std::unique_ptr<Group> root;
    for (auto& g : source.group) {
        auto new_group = std::make_unique<Group>();
        assert(g.nodes.size() < UINT16_MAX);
        new_group->nested_leaf_count = static_cast<uint16_t>(g.nodes.size());
        if (root) {
            insert_new_group_into_tree(std::move(new_group), g, *root);
        } else {
            root = std::move(new_group);
        }
    }
    return root;
}

/* Even though groups are inherently hierarchical, the config is a flat array with a
 * hierarchy bolted on through the use of (more or less) "multi-dimensional" index strings.
 * Index string of root group is always "invalid" (or possibly some other string that cannot
 * be interpreted as a dot-separated tree node path). Other groups have an index of the
 * form "X.Y.Z", where Z is the group's immediate parent index, Y is Z's parent and so on. Just
 * stating Z itself is not sufficient to uniquely identify the group, as group indices are
 * not unique _across_ groups. For indices "0.1" and "1.1", the trailing "1" refers to 2
 * distinct groups, as they have different parents.
 *
 * It may be noted that the group index strings do _not_ include the root group, so we
 * have to always implicitly include it ourselves.
 *
 * Config groups are ordered so that when a group is encountered, all its parents (and
 * transitively, its parents again etc) have already been processed. This directly
 * implies that the root group is always the first group present in the config.
 */
void build_global_groups(DistributionConfigBuilder& builder, const DistributionConfig& source) {
    assert(!source.group.empty()); // TODO gracefully handle empty config?
    auto root = create_group_tree_from_config(source);

    auto g_iter = source.group.begin();
    const auto g_end = source.group.end();
    build_transformed_root_group(builder, *g_iter, *root);
    ++g_iter;
    for (; g_iter != g_end; ++g_iter) {
        build_transformed_non_root_group(builder, *g_iter, *root);
    }

    builder.redundancy = root->nested_leaf_count;
    builder.readyCopies = builder.redundancy;
}

} // anon ns

std::shared_ptr<DistributionConfig>
GlobalBucketSpaceDistributionConverter::convert_to_global(const DistributionConfig& source) {
    DistributionConfigBuilder builder;
    set_distribution_invariant_config_fields(builder, source);
    build_global_groups(builder, source);
    return std::make_shared<DistributionConfig>(builder);
}

std::shared_ptr<lib::Distribution>
GlobalBucketSpaceDistributionConverter::convert_to_global(const lib::Distribution& distr) {
    const auto src_config = distr.serialize();
    auto global_config = convert_to_global(*string_to_config(src_config));
    return std::make_shared<lib::Distribution>(*global_config);
}

std::unique_ptr<DistributionConfig>
GlobalBucketSpaceDistributionConverter::string_to_config(const vespalib::string& cfg) {
    vespalib::asciistream iss(cfg);
    config::AsciiConfigReader<vespa::config::content::StorDistributionConfig> reader(iss);
    return reader.read();
}

vespalib::string GlobalBucketSpaceDistributionConverter::config_to_string(const DistributionConfig& cfg) {
    vespalib::asciistream ost;
    config::AsciiConfigWriter writer(ost);
    writer.write(cfg);
    return ost.str();
}

}
