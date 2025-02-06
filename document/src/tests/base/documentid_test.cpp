// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for documentid.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/idstringexception.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;
using std::string;

namespace {

const string ns = "namespace";
const string ns_id = "namespaceid";
const string type = "my_type";

void checkId(const string &id, const string &ns_str, const string local_id) {
    DocumentId doc_id(id);
    EXPECT_EQ(ns_str, doc_id.getScheme().getNamespace());
    EXPECT_EQ(local_id, doc_id.getScheme().getNamespaceSpecific());
    EXPECT_EQ(id, doc_id.getScheme().toString());
}

template <typename IdType>
const IdType &getAs(const DocumentId &doc_id) {
    return dynamic_cast<const IdType&>(doc_id.getScheme());
}

template <typename IdType>
void checkUser(const string &id, uint64_t user_id) {
    DocumentId doc_id(id);
    EXPECT_EQ(user_id, getAs<IdType>(doc_id).getNumber());
}

void checkType(const string &id, const string &doc_type) {
    DocumentId doc_id(id);
    ASSERT_TRUE(doc_id.hasDocType());
    EXPECT_EQ(doc_type, doc_id.getDocType());
}

TEST(DocumentIdTest, require_that_id_id_can_be_parsed)
{
    const string id = "id:" + ns + ":" + type + "::" + ns_id;
    checkId(id, ns, ns_id);
    checkType(id, type);
}

TEST(DocumentIdTest, require_that_we_allow_colon_in_namespace_specific_part)
{
    const string nss=":a:b:c:";
    string id="id:" + ns + ":" + type + "::" + nss;
    checkId(id, ns, nss);
    checkType(id, type);
}

TEST(DocumentIdTest, require_that_id_id_can_specify_location)
{
    DocumentId id("id:ns:type:n=12345:foo");
    EXPECT_EQ(12345u, id.getScheme().getLocation());
    EXPECT_EQ(12345u, getAs<IdString>(id).getNumber());
}

TEST(DocumentIdTest, require_that_id_ids_n_key_must_be_a_64_bit_number)
{
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:n=abc:foo"), IdParseException,
                           "'n'-value must be a 64-bit number");
    DocumentId("id:ns:type:n=18446744073709551615:foo");  // ok
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:n=18446744073709551616:foo"),
                           IdParseException, "'n'-value out of range");
}

TEST(DocumentIdTest, require_that_id_id_can_specify_group)
{
    DocumentId id1("id:ns:type:g=mygroup:foo");
    DocumentId id2("id:ns:type:g=mygroup:bar");
    DocumentId id3("id:ns:type:g=other group:baz");
    EXPECT_EQ(id1.getScheme().getLocation(), id2.getScheme().getLocation());
    EXPECT_NE(id1.getScheme().getLocation(),
                     id3.getScheme().getLocation());
    EXPECT_EQ("mygroup", getAs<IdString>(id1).getGroup());
}

TEST(DocumentIdTest, require_that_id_id_location_is_specified_by_local_id_only_by_default)
{
    DocumentId id1("id:ns:type::locationspec");
    DocumentId id2("id:ns:type:g=locationspec:bar");
    EXPECT_EQ("locationspec", id1.getScheme().getNamespaceSpecific());
    EXPECT_EQ("bar", id2.getScheme().getNamespaceSpecific());
    EXPECT_EQ(id1.getScheme().getLocation(), id2.getScheme().getLocation());
}

TEST(DocumentIdTest, require_that_local_id_can_be_empty)
{
    const string id = "id:" + ns + ":type:n=1234:";
    checkId(id, ns, "");
    checkUser<IdString>(id, 1234);
}

TEST(DocumentIdTest, require_that_document_ids_can_be_assigned)
{
    DocumentId id1("id:" + ns + ":type:n=1234:");
    DocumentId id2 = id1;
    checkId(id2.toString(), ns, "");
    checkUser<IdString>(id2.toString(), 1234);
}

TEST(DocumentIdTest, require_that_illegal_ids_fail)
{
    VESPA_EXPECT_EXCEPTION(DocumentId("idg:foo:bar:baz"), IdParseException,
                           "No scheme separator ':' found");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:"), IdParseException, "too short");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns"), IdParseException,
                           "No namespace separator ':' found");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type"), IdParseException,
                           "No document type separator ':' found");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:kv_pair"), IdParseException,
                           "No key/value-pairs separator ':' found");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:k=foo:bar"), IdParseException,
                           "Illegal key 'k'");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:n=0,n=1:bar"), IdParseException,
                           "Illegal key combination in n=0,n=1");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:g=foo,g=ba:bar"), IdParseException,
                           "Illegal key combination in g=foo,g=ba");
    VESPA_EXPECT_EXCEPTION(DocumentId("id:ns:type:n=0,g=foo:bar"), IdParseException,
                           "Illegal key combination in n=0,g=foo");
}

TEST(DocumentIdTest, require_that_key_value_pairs_in_id_id_are_preserved)
{
    const string id_str1 = "id:ns:type:n=1:foo";
    EXPECT_EQ(id_str1, DocumentId(id_str1).toString());

    const string id_str2 = "id:ns:type:g=mygroup:foo";
    EXPECT_EQ(id_str2, DocumentId(id_str2).toString());
}

void verifyGroupLocation(string s, string group, uint64_t location) {
    DocumentId d(s);
    SCOPED_TRACE(s);
    EXPECT_TRUE(d.getScheme().hasGroup());
    EXPECT_EQ(s, d.toString());
    EXPECT_EQ(group, d.getScheme().getGroup());
    EXPECT_EQ(location, d.getScheme().getLocation()&0xffffffff);
}

TEST(DocumentIdTest, require_that_0x3d_char_is_handled_correctly_in_group_ids)
{
    verifyGroupLocation("id:x:foo:g=X:bar",  "X",  0xb89b1202);
    verifyGroupLocation("id:x:foo:g=X=:bar", "X=", 0xb61ca7e1);
    verifyGroupLocation("id:x:foo:g=X=:foo", "X=", 0xb61ca7e1);
}

TEST(DocumentIdTest, require_that_id_strings_reports_features_hasNumber_hasGroup)
{
    DocumentId none("id:ns:type::foo");
    EXPECT_FALSE(none.getScheme().hasNumber());
    EXPECT_FALSE(none.getScheme().hasGroup());
    EXPECT_EQ("foo", none.getScheme().getNamespaceSpecific());

    DocumentId user("id:ns:type:n=42:foo");
    EXPECT_TRUE(user.getScheme().hasNumber());
    EXPECT_FALSE(user.getScheme().hasGroup());
    EXPECT_EQ(42u, user.getScheme().getNumber());
    EXPECT_EQ("foo", user.getScheme().getNamespaceSpecific());

    DocumentId group("id:ns:type:g=mygroup:foo");
    EXPECT_FALSE(group.getScheme().hasNumber());
    EXPECT_TRUE(group.getScheme().hasGroup());
    EXPECT_EQ("mygroup", group.getScheme().getGroup());
    EXPECT_EQ("foo", group.getScheme().getNamespaceSpecific());
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
