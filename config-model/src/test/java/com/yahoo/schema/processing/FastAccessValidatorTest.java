// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.test.TestUtil;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author bjorncs
 */
public class FastAccessValidatorTest {

    @SuppressWarnings("deprecation")
    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_on_incompatible_use_of_fastaccess() throws ParseException {
        ApplicationBuilder builder = new ApplicationBuilder(new RankProfileRegistry());
        builder.addSchema(
                TestUtil.joinLines(
                        "schema parent {",
                        "  document parent {",
                        "    field int_field type int { indexing: attribute }",
                        "  }",
                        "}"));
        builder.addSchema(
                TestUtil.joinLines(
                        "schema test {",
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
                "For schema 'test': The following attributes have a type that is incompatible " +
                        "with fast-access: predicate_attribute, tensor_attribute, reference_attribute. " +
                        "Predicate, tensor and reference attributes are incompatible with fast-access.");
        builder.build(true);
    }

}
