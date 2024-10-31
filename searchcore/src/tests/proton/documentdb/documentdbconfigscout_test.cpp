// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/server/documentdbconfig.h>
#include <vespa/searchcore/proton/server/documentdbconfigscout.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ostream>

using namespace document;
using namespace proton;
using namespace search;
using namespace search::index;
using namespace vespa::config::search;
using std::shared_ptr;
using std::make_shared;

using DDBCSP = shared_ptr<DocumentDBConfig>;

namespace vespa::config::search::internal {

std::ostream& operator<<(std::ostream& os, const AttributesConfig::Attribute::Match match) {
    os << AttributesConfig::Attribute::getMatchName(match);
    return os;
}

std::ostream& operator<<(std::ostream& os, const AttributesConfig::Attribute::Dictionary::Match match) {
    os << AttributesConfig::Attribute::Dictionary::getMatchName(match);
    return os;
}

std::ostream& operator<<(std::ostream& os, const AttributesConfig::Attribute::Dictionary::Type type) {
    os << AttributesConfig::Attribute::Dictionary::getTypeName(type);
    return os;
}

}

namespace {

DDBCSP
getConfig(int64_t generation, std::shared_ptr<const Schema> schema,
          const shared_ptr<const DocumentTypeRepo> & repo,
          const AttributesConfig &attributes)
{
    return test::DocumentDBConfigBuilder(generation, std::move(schema), "client", "test").
            repo(repo).attributes(make_shared<AttributesConfig>(attributes)).build();
}


bool
assertDefaultAttribute(const AttributesConfig::Attribute &attribute,
                       const std::string &name)
{
    SCOPED_TRACE(name);
    bool failed = false;
    EXPECT_EQ(name, attribute.name) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_FALSE(attribute.fastsearch) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_FALSE(attribute.paged) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_FALSE(attribute.enableonlybitvector) << (failed = true, "");
    return !failed;
}

bool
assert_string_attribute(const AttributesConfig::Attribute& attribute,
                        const std::string& name,
                        std::optional<bool> uncased, std::optional<AttributesConfig::Attribute::Dictionary::Type> dictionary_type)
{
    SCOPED_TRACE(name);
    using Attribute = AttributesConfig::Attribute;
    using Dictionary = Attribute::Dictionary;
    using Match = Attribute::Match;
    if (!assertDefaultAttribute(attribute, name)) {
        return false;
    }
    bool failed = false;
    EXPECT_EQ(name, attribute.name) << (failed = true, "");
    if (failed) {
        return false;
    }
    if (uncased.has_value()) {
        if (uncased.value()) {
            EXPECT_EQ(Match::UNCASED, attribute.match) << (failed = true, "");
            if (failed) {
                return false;
            }
            EXPECT_EQ(Dictionary::Match::UNCASED, attribute.dictionary.match) << (failed = true, "");
            if (failed) {
                return false;
            }
        } else {
            EXPECT_EQ(Match::CASED, attribute.match) << (failed = true, "");
            if (failed) {
                return false;
            }
            EXPECT_EQ(Dictionary::Match::CASED, attribute.dictionary.match) << (failed = true, "");
            if (failed) {
                return false;
            }
        }
    }
    if (dictionary_type.has_value()) {
        EXPECT_EQ(dictionary_type.value(), attribute.dictionary.type) << (failed = true, "");
        if (failed) {
            return false;
        }
    }
    return true;
}

bool
assertFastSearchAttribute(const AttributesConfig::Attribute &attribute,
                          const std::string &name)
{
    SCOPED_TRACE(name);
    bool failed = false;
    EXPECT_EQ(name, attribute.name) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(attribute.fastsearch) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_FALSE(attribute.paged) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_FALSE(attribute.enableonlybitvector) << (failed = true, "");
    return !failed;
}


bool
assertFastSearchAndMoreAttribute(const AttributesConfig::Attribute &attribute,
                                 const std::string &name)
{
    SCOPED_TRACE(name);
    bool failed = false;
    EXPECT_EQ(name, attribute.name) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(attribute.fastsearch) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(attribute.paged) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(attribute.enableonlybitvector) << (failed = true, "");
    return !failed;
}

bool
assertTensorAttribute(const AttributesConfig::Attribute &attribute,
                      const std::string &name, const std::string &spec, int max_links_per_node)
{
    SCOPED_TRACE(name);
    bool failed = false;
    EXPECT_EQ(attribute.name, name) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ((int)attribute.datatype, (int)AttributesConfig::Attribute::Datatype::TENSOR) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(attribute.tensortype, spec) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(attribute.index.hnsw.enabled) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(attribute.index.hnsw.maxlinkspernode, max_links_per_node) << (failed = true, "");
    return !failed;
}

bool
assertAttributes(const AttributesConfig::AttributeVector &attributes)
{
    bool failed = false;
    EXPECT_EQ(8u, attributes.size()) << (failed = true, "");
    if (failed) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[0], "a1")) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[1], "a2")) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[2], "a3")) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[3], "a4")) {
        return false;
    }
    if (!assertTensorAttribute(attributes[4], "tensor1", "tensor(x[100])", 16)) {
        return false;
    }                                                                         
    if (!assertTensorAttribute(attributes[5], "tensor2", "tensor(x[100])", 16)) {
        return false;
    }
    if (!assert_string_attribute(attributes[6], "string1", std::nullopt, AttributesConfig::Attribute::Dictionary::Type::BTREE)) {
        return false;
    }
    if (!assert_string_attribute(attributes[7], "string2", true, std::nullopt)) {
        return false;
    }
    return true;
}


bool
assertLiveAttributes(const AttributesConfig::AttributeVector &attributes)
{
    bool failed = false;
    EXPECT_EQ(9u, attributes.size()) << (failed = true, "");
    if (failed) {
        return false;
    }
    if (!assertFastSearchAttribute(attributes[0], "a0")) {
        return false;
    }
    if (!assertFastSearchAndMoreAttribute(attributes[1], "a1")) {
        return false;
    }
    if (!assertFastSearchAttribute(attributes[2], "a2")) {
        return false;
    }
    if (!assertFastSearchAttribute(attributes[3], "a3")) {
        return false;
    }
    if (!assertFastSearchAttribute(attributes[4], "a4")) {
        return false;
    }
    if (!assertTensorAttribute(attributes[5], "tensor1", "tensor(x[100])", 32)) {
        return false;
    }                                                                         
    if (!assertTensorAttribute(attributes[6], "tensor2", "tensor(x[200])", 32)) {
        return false;
    }                                                                         
    if (!assert_string_attribute(attributes[7], "string1", std::nullopt, AttributesConfig::Attribute::Dictionary::Type::HASH)) {
        return false;
    }
    if (!assert_string_attribute(attributes[8], "string2", false, std::nullopt)) {
        return false;
    }
    return true;
}


bool
assertScoutedAttributes(const AttributesConfig::AttributeVector &attributes)
{
    bool failed = false;
    EXPECT_EQ(8u, attributes.size()) << (failed = true, "");
    if (failed) {
        return false;
    }
    if (!assertFastSearchAndMoreAttribute(attributes[0], "a1")) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[1], "a2")) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[2], "a3")) {
        return false;
    }
    if (!assertDefaultAttribute(attributes[3], "a4")) {
        return false;
    }
    if (!assertTensorAttribute(attributes[4], "tensor1", "tensor(x[100])", 32)) {
        return false;
    }                                                                         
    if (!assertTensorAttribute(attributes[5], "tensor2", "tensor(x[100])", 16)) {
        return false;
    }                                                                         
    if (!assert_string_attribute(attributes[6], "string1", std::nullopt, AttributesConfig::Attribute::Dictionary::Type::HASH)) {
        return false;
    }
    if (!assert_string_attribute(attributes[7], "string2", false, std::nullopt)) {
        return false;
    }
    return true;
}


AttributesConfig::Attribute
setupDefaultAttribute(const std::string & name)
{
    AttributesConfig::Attribute attribute;
    attribute.name = name;
    return attribute;
}

AttributesConfig::Attribute
setup_string_attribute(const std::string& name, std::optional<bool> uncased, std::optional<AttributesConfig::Attribute::Dictionary::Type> dictionary_type)
{
    using Attribute = AttributesConfig::Attribute;
    using Datatype = Attribute::Datatype;
    using Dictionary = Attribute::Dictionary;
    using Match = Attribute::Match;
    Attribute attribute;
    attribute.name = name;
    attribute.datatype = Datatype::STRING;
    if (uncased.has_value()) {
        if (uncased.value()) {
            attribute.match = Match::UNCASED;
            attribute.dictionary.match = Dictionary::Match::UNCASED;
        } else {
            attribute.match = Match::CASED;
            attribute.dictionary.match = Dictionary::Match::CASED;
        }
    }
    if (dictionary_type.has_value()) {
        attribute.dictionary.type = dictionary_type.value();
    }
    return attribute;
}

AttributesConfig::Attribute
setupFastSearchAttribute(const std::string & name)
{
    AttributesConfig::Attribute attribute;
    attribute.name = name;
    attribute.fastsearch = true;
    return attribute;
}


AttributesConfig::Attribute
setupFastSearchAndMoreAttribute(const std::string & name)
{
    AttributesConfig::Attribute attribute;
    attribute.name = name;
    attribute.fastsearch = true;
    attribute.paged = true;
    attribute.enableonlybitvector = true;
    return attribute;
}

AttributesConfig::Attribute
setupTensorAttribute(const std::string &name, const std::string &spec, int max_links_per_node)
{
    AttributesConfig::Attribute attribute;
    attribute.name = name;
    attribute.datatype = AttributesConfig::Attribute::Datatype::TENSOR;
    attribute.tensortype = spec;
    attribute.index.hnsw.enabled = true;
    attribute.index.hnsw.maxlinkspernode = max_links_per_node;
    return attribute;
}

void
setupDefaultAttributes(AttributesConfigBuilder::AttributeVector &attributes)
{
    attributes.push_back(setupDefaultAttribute("a1"));
    attributes.push_back(setupDefaultAttribute("a2"));
    attributes.push_back(setupDefaultAttribute("a3"));
    attributes.push_back(setupDefaultAttribute("a4"));
    attributes.push_back(setupTensorAttribute("tensor1", "tensor(x[100])", 16));
    attributes.push_back(setupTensorAttribute("tensor2", "tensor(x[100])", 16));
    attributes.push_back(setup_string_attribute("string1", std::nullopt, AttributesConfig::Attribute::Dictionary::Type::BTREE));
    attributes.push_back(setup_string_attribute("string2", true, std::nullopt));
}


void
setupLiveAttributes(AttributesConfigBuilder::AttributeVector &attributes)
{
    attributes.push_back(setupFastSearchAttribute("a0"));
    attributes.push_back(setupFastSearchAndMoreAttribute("a1"));
    attributes.push_back(setupFastSearchAttribute("a2"));
    attributes.back().datatype = AttributesConfig::Attribute::Datatype::INT8;
    attributes.push_back(setupFastSearchAttribute("a3"));
    attributes.back().collectiontype = AttributesConfig::Attribute::Collectiontype::ARRAY;
    attributes.push_back(setupFastSearchAttribute("a4"));
    attributes.back().createifnonexistent = true;
    attributes.push_back(setupTensorAttribute("tensor1", "tensor(x[100])", 32));
    attributes.push_back(setupTensorAttribute("tensor2", "tensor(x[200])", 32));
    attributes.push_back(setup_string_attribute("string1", std::nullopt, AttributesConfig::Attribute::Dictionary::Type::HASH));
    attributes.push_back(setup_string_attribute("string2", false, std::nullopt));
}

}

TEST(DocumentDBConfigScoutTest, Test_that_DocumentDBConfigScout_scout_looks_ahead)
{
    AttributesConfigBuilder attributes;
    setupDefaultAttributes(attributes.attribute);
    
    AttributesConfigBuilder liveAttributes;
    setupLiveAttributes(liveAttributes.attribute);

    shared_ptr<const DocumentTypeRepo> repo(make_shared<DocumentTypeRepo>());
    std::shared_ptr<const Schema> schema(make_shared<Schema>());
    DDBCSP cfg = getConfig(4, schema, repo, attributes);
    DDBCSP liveCfg = getConfig(4, schema, repo, liveAttributes);
    EXPECT_FALSE(*cfg == *liveCfg);
    DDBCSP scoutedCfg = DocumentDBConfigScout::scout(cfg, *liveCfg);
    EXPECT_FALSE(*cfg == *scoutedCfg);
    EXPECT_FALSE(*liveCfg == *scoutedCfg);
    
    EXPECT_TRUE(assertAttributes(cfg->getAttributesConfig().attribute));
    EXPECT_TRUE(assertLiveAttributes(liveCfg->getAttributesConfig().attribute));
    EXPECT_TRUE(assertScoutedAttributes(scoutedCfg->getAttributesConfig().
                                        attribute));
}
