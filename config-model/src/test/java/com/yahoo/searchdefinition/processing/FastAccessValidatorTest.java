// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.test.TestUtil;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author bjorncs
 */
public class FastAccessValidatorTest {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_on_incompatible_use_of_fastaccess() throws ParseException {
        SearchBuilder builder = new SearchBuilder(new RankProfileRegistry());
        builder.importString(
                TestUtil.joinLines(
                        "search parent {",
                        "  document parent {",
                        "    field int_field type int { indexing: attribute }",
                        "  }",
                        "}"));
        builder.importString(
                TestUtil.joinLines(
                        "search test {",
                        "    document test { ",
                        "        field int_attribute type int { ",
                        "            indexing: attribute ",
                        "            attribute: fast-access",
                        "        }",
                        "        field predicate_attribute type predicate {",
                        "            indexing: attribute ",
                        "            attribute: fast-access",
                        "        }",
                        "        field tensor_attribute type tensor(x[5]) {",
                        "            indexing: attribute ",
                        "            attribute: fast-access",
                        "        }",
                        "        field reference_attribute type reference<parent> {",
                        "            indexing: attribute ",
                        "            attribute: fast-access",
                        "        }",
                        "    }",
                        "}"));
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For search 'test': The following attributes have a type that is incompatible " +
                        "with fast-access: predicate_attribute, tensor_attribute, reference_attribute. " +
                        "Predicate, tensor and reference attributes are incompatible with fast-access.");
        builder.build();
    }

}
