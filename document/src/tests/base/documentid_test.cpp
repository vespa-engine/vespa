// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for documentid.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/idstringexception.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace document;
using vespalib::string;

namespace {

const string ns = "namespace";
const string ns_id = "namespaceid";
const string type = "my_type";

void checkId(const string &id, const string &ns_str, const string local_id) {
    DocumentId doc_id(id);
    EXPECT_EQUAL(ns_str, doc_id.getScheme().getNamespace());
    EXPECT_EQUAL(local_id, doc_id.getScheme().getNamespaceSpecific());
    EXPECT_EQUAL(id, doc_id.getScheme().toString());
}

template <typename IdType>
const IdType &getAs(const DocumentId &doc_id) {
    const IdType *id_str = dynamic_cast<const IdType *>(&doc_id.getScheme());
    ASSERT_TRUE(id_str);
    return *id_str;
}

template <typename IdType>
void checkUser(const string &id, uint64_t user_id) {
    DocumentId doc_id(id);
    EXPECT_EQUAL(user_id, getAs<IdType>(doc_id).getNumber());
}

void checkType(const string &id, const string &doc_type) {
    DocumentId doc_id(id);
    ASSERT_TRUE(doc_id.hasDocType());
    EXPECT_EQUAL(doc_type, doc_id.getDocType());
}

TEST("require that id id can be parsed") {
    const string id = "id:" + ns + ":" + type + "::" + ns_id;
    checkId(id, ns, ns_id);
    checkType(id, type);
}

TEST("require that we allow ':' in namespace specific part") {
    const string nss=":a:b:c:";
    string id="id:" + ns + ":" + type + "::" + nss;
    checkId(id, ns, nss);
    checkType(id, type);
}

TEST("require that id id can specify location") {
    DocumentId id("id:ns:type:n=12345:foo");
    EXPECT_EQUAL(12345u, id.getScheme().getLocation());
    EXPECT_EQUAL(12345u, getAs<IdString>(id).getNumber());
}

TEST("require that id id's n key must be a 64-bit number") {
    EXPECT_EXCEPTION(DocumentId("id:ns:type:n=abc:foo"), IdParseException,
                     "'n'-value must be a 64-bit number");
    DocumentId("id:ns:type:n=18446744073709551615:foo");  // ok
    EXPECT_EXCEPTION(DocumentId("id:ns:type:n=18446744073709551616:foo"),
                     IdParseException, "'n'-value out of range");
}

TEST("require that id id can specify group") {
    DocumentId id1("id:ns:type:g=mygroup:foo");
    DocumentId id2("id:ns:type:g=mygroup:bar");
    DocumentId id3("id:ns:type:g=other group:baz");
    EXPECT_EQUAL(id1.getScheme().getLocation(), id2.getScheme().getLocation());
    EXPECT_NOT_EQUAL(id1.getScheme().getLocation(),
                     id3.getScheme().getLocation());
    EXPECT_EQUAL("mygroup", getAs<IdString>(id1).getGroup());
}

TEST("require that id id location is specified by local id only by default") {
    DocumentId id1("id:ns:type::locationspec");
    DocumentId id2("id:ns:type:g=locationspec:bar");
    EXPECT_EQUAL("locationspec", id1.getScheme().getNamespaceSpecific());
    EXPECT_EQUAL("bar", id2.getScheme().getNamespaceSpecific());
    EXPECT_EQUAL(id1.getScheme().getLocation(), id2.getScheme().getLocation());
}

TEST("require that local id can be empty") {
    const string id = "id:" + ns + ":type:n=1234:";
    checkId(id, ns, "");
    checkUser<IdString>(id, 1234);
}

TEST("require that document ids can be assigned") {
    DocumentId id1("id:" + ns + ":type:n=1234:");
    DocumentId id2 = id1;
    checkId(id2.toString(), ns, "");
    checkUser<IdString>(id2.toString(), 1234);
}

TEST("require that illegal ids fail") {
    EXPECT_EXCEPTION(DocumentId("idg:foo:bar:baz"), IdParseException,
                     "No scheme separator ':' found");
    EXPECT_EXCEPTION(DocumentId("id:"), IdParseException, "too short");
    EXPECT_EXCEPTION(DocumentId("id:ns"), IdParseException,
                     "No namespace separator ':' found");
    EXPECT_EXCEPTION(DocumentId("id:ns:type"), IdParseException,
                     "No document type separator ':' found");
    EXPECT_EXCEPTION(DocumentId("id:ns:type:kv_pair"), IdParseException,
                     "No key/value-pairs separator ':' found");
    EXPECT_EXCEPTION(DocumentId("id:ns:type:k=foo:bar"), IdParseException,
                     "Illegal key 'k'");
    EXPECT_EXCEPTION(DocumentId("id:ns:type:n=0,n=1:bar"), IdParseException,
                     "Illegal key combination in n=0,n=1");
    EXPECT_EXCEPTION(DocumentId("id:ns:type:g=foo,g=ba:bar"), IdParseException,
                     "Illegal key combination in g=foo,g=ba");
    EXPECT_EXCEPTION(DocumentId("id:ns:type:n=0,g=foo:bar"), IdParseException,
                     "Illegal key combination in n=0,g=foo");
}

TEST("require that key-value pairs in id id are preserved") {
    const string id_str1 = "id:ns:type:n=1:foo";
    EXPECT_EQUAL(id_str1, DocumentId(id_str1).toString());

    const string id_str2 = "id:ns:type:g=mygroup:foo";
    EXPECT_EQUAL(id_str2, DocumentId(id_str2).toString());
}

void verifyGroupLocation(string s, string group, uint64_t location) {
    DocumentId d(s);
    EXPECT_TRUE(d.getScheme().hasGroup());
    EXPECT_EQUAL(s, d.toString());
    EXPECT_EQUAL(group, d.getScheme().getGroup());
    EXPECT_EQUAL(location, d.getScheme().getLocation()&0xffffffff);
}

TEST("require that = is handled correctly in group ids") {
    TEST_DO(verifyGroupLocation("id:x:foo:g=X:bar",  "X",  0xb89b1202));
    TEST_DO(verifyGroupLocation("id:x:foo:g=X=:bar", "X=", 0xb61ca7e1));
    TEST_DO(verifyGroupLocation("id:x:foo:g=X=:foo", "X=", 0xb61ca7e1));
}

TEST("require that id strings reports features (hasNumber, hasGroup)") {
    DocumentId none("id:ns:type::foo");
    EXPECT_FALSE(none.getScheme().hasNumber());
    EXPECT_FALSE(none.getScheme().hasGroup());
    EXPECT_EQUAL("foo", none.getScheme().getNamespaceSpecific());

    DocumentId user("id:ns:type:n=42:foo");
    EXPECT_TRUE(user.getScheme().hasNumber());
    EXPECT_FALSE(user.getScheme().hasGroup());
    EXPECT_EQUAL(42u, user.getScheme().getNumber());
    EXPECT_EQUAL("foo", user.getScheme().getNamespaceSpecific());

    DocumentId group("id:ns:type:g=mygroup:foo");
    EXPECT_FALSE(group.getScheme().hasNumber());
    EXPECT_TRUE(group.getScheme().hasGroup());
    EXPECT_EQUAL("mygroup", group.getScheme().getGroup());
    EXPECT_EQUAL("foo", group.getScheme().getNamespaceSpecific());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
