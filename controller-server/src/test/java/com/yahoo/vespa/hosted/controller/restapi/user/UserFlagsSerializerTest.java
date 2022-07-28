// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.JsonNodeRawFlag;
import com.yahoo.vespa.flags.json.Condition;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.Rule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.FetchVector.Dimension.TENANT_ID;

/**
 * @author freva
 */
public class UserFlagsSerializerTest {

    @Test
    void user_flag_test() throws IOException {
        String email1 = "alice@domain.tld";
        String email2 = "bob@domain.tld";

        try (Flags.Replacer ignored = Flags.clearFlagsForTesting()) {
            Flags.defineStringFlag("string-id", "default value", List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod", CONSOLE_USER_EMAIL);
            Flags.defineIntFlag("int-id", 123, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod", CONSOLE_USER_EMAIL, TENANT_ID, APPLICATION_ID);
            Flags.defineDoubleFlag("double-id", 3.14d, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod");
            Flags.defineListFlag("list-id", List.of("a"), String.class, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod", CONSOLE_USER_EMAIL);
            Flags.defineJacksonFlag("jackson-id", new ExampleJacksonClass(123, "abc"), ExampleJacksonClass.class,
                    List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod", CONSOLE_USER_EMAIL, TENANT_ID);

            Map<FlagId, FlagData> flagData = Stream.of(
                    flagData("string-id", rule("\"value1\"", condition(CONSOLE_USER_EMAIL, Condition.Type.WHITELIST, email1))),
                    flagData("int-id", rule("456")),
                    flagData("list-id",
                            rule("[\"value1\"]", condition(CONSOLE_USER_EMAIL, Condition.Type.WHITELIST, email1), condition(APPLICATION_ID, Condition.Type.BLACKLIST, "tenant1:video:default", "tenant1:video:default", "tenant2:music:default")),
                            rule("[\"value2\"]", condition(CONSOLE_USER_EMAIL, Condition.Type.WHITELIST, email2)),
                            rule("[\"value1\",\"value3\"]", condition(APPLICATION_ID, Condition.Type.BLACKLIST, "tenant1:video:default", "tenant1:video:default", "tenant2:music:default"))),
                    flagData("jackson-id", rule("{\"integer\":456,\"string\":\"xyz\"}", condition(CONSOLE_USER_EMAIL, Condition.Type.WHITELIST, email1), condition(TENANT_ID, Condition.Type.WHITELIST, "tenant1", "tenant3")))
            ).collect(Collectors.toMap(FlagData::id, fd -> fd));

            // double-id is not here as it does not have CONSOLE_USER_EMAIL dimension
            assertUserFlags("{\"flags\":[" +
                    "{\"id\":\"int-id\",\"rules\":[{\"value\":456}]}," + // Default from DB
                    "{\"id\":\"jackson-id\",\"rules\":[{\"conditions\":[{\"type\":\"whitelist\",\"dimension\":\"tenant\"}],\"value\":{\"integer\":456,\"string\":\"xyz\"}},{\"value\":{\"integer\":123,\"string\":\"abc\"}}]}," + // Resolved for email
                    // Resolved for email, but conditions are empty since this user is not authorized for any tenants
                    "{\"id\":\"list-id\",\"rules\":[{\"conditions\":[{\"type\":\"blacklist\",\"dimension\":\"application\"}],\"value\":[\"value1\"]},{\"conditions\":[{\"type\":\"blacklist\",\"dimension\":\"application\"}],\"value\":[\"value1\",\"value3\"]},{\"value\":[\"a\"]}]}," +
                    "{\"id\":\"string-id\",\"rules\":[{\"value\":\"value1\"}]}]}", // resolved for email
                    flagData, Set.of(), false, email1);

            // Same as the first one, but user is authorized for tenant1
            assertUserFlags("{\"flags\":[" +
                    "{\"id\":\"int-id\",\"rules\":[{\"value\":456}]}," + // Default from DB
                    "{\"id\":\"jackson-id\",\"rules\":[{\"conditions\":[{\"type\":\"whitelist\",\"dimension\":\"tenant\",\"values\":[\"tenant1\"]}],\"value\":{\"integer\":456,\"string\":\"xyz\"}},{\"value\":{\"integer\":123,\"string\":\"abc\"}}]}," + // Resolved for email
                    // Resolved for email, but conditions have filtered out tenant2
                    "{\"id\":\"list-id\",\"rules\":[{\"conditions\":[{\"type\":\"blacklist\",\"dimension\":\"application\",\"values\":[\"tenant1:video:default\",\"tenant1:video:default\"]}],\"value\":[\"value1\"]},{\"conditions\":[{\"type\":\"blacklist\",\"dimension\":\"application\",\"values\":[\"tenant1:video:default\",\"tenant1:video:default\"]}],\"value\":[\"value1\",\"value3\"]},{\"value\":[\"a\"]}]}," +
                    "{\"id\":\"string-id\",\"rules\":[{\"value\":\"value1\"}]}]}", // resolved for email
                    flagData, Set.of("tenant1"), false, email1);

            // As operator no conditions are filtered, but the email precondition is applied
            assertUserFlags("{\"flags\":[" +
                    "{\"id\":\"int-id\",\"rules\":[{\"value\":456}]}," + // Default from DB
                    "{\"id\":\"jackson-id\",\"rules\":[{\"value\":{\"integer\":123,\"string\":\"abc\"}}]}," + // Default from code, no DB values match
                    // Includes last value from DB which is not conditioned on email and the default from code
                    "{\"id\":\"list-id\",\"rules\":[{\"conditions\":[{\"type\":\"blacklist\",\"dimension\":\"application\",\"values\":[\"tenant1:video:default\",\"tenant1:video:default\",\"tenant2:music:default\"]}],\"value\":[\"value1\",\"value3\"]},{\"value\":[\"a\"]}]}," +
                    "{\"id\":\"string-id\",\"rules\":[{\"value\":\"default value\"}]}]}", // Default from code
                    flagData, Set.of(), true, "operator@domain.tld");
        }
    }

    private static FlagData flagData(String id, Rule... rules) {
        return new FlagData(new FlagId(id), new FetchVector(), rules);
    }

    private static Rule rule(String data, Condition... conditions) {
        return new Rule(Optional.ofNullable(data).map(JsonNodeRawFlag::fromJson), conditions);
    }

    private static Condition condition(FetchVector.Dimension dimension, Condition.Type type, String... values) {
        return new Condition.CreateParams(dimension).withValues(values).createAs(type);
    }

    private static void assertUserFlags(String expected, Map<FlagId, FlagData> rawFlagData,
                                        Set<String> authorizedForTenantNames, boolean isOperator, String userEmail) throws IOException {
        Slime slime = new Slime();
        UserFlagsSerializer.toSlime(slime.setObject(), rawFlagData, authorizedForTenantNames.stream().map(TenantName::from).collect(Collectors.toSet()), isOperator, userEmail);
        JsonTestHelper.assertJsonEquals(expected,
                new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExampleJacksonClass {
        @JsonProperty("integer") public final int integer;
        @JsonProperty("string") public final String string;
        private ExampleJacksonClass(@JsonProperty("integer") int integer, @JsonProperty("string") String string) {
            this.integer = integer;
            this.string = string;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExampleJacksonClass that = (ExampleJacksonClass) o;
            return integer == that.integer &&
                    Objects.equals(string, that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integer, string);
        }
    }
}