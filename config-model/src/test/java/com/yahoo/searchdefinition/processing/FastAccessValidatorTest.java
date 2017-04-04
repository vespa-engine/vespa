package com.yahoo.searchdefinition.processing;

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
                "search test {\n" +
                "    document test { \n" +
                "        field int_attribute type int { \n" +
                "            indexing: attribute \n" +
                "            attribute: fast-access\n" +
                "        }\n" +
                "        field predicate_attribute type predicate {\n" +
                "            indexing: attribute \n" +
                "            attribute: fast-access\n" +
                "        }\n" +
                "        field tensor_attribute type tensor(x[]) {\n" +
                "            indexing: attribute \n" +
                "            attribute: fast-access\n" +
                "        }\n" +
                "    }\n" +
                "}\n");
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For search 'test': The following attributes have a type that is incompatible " +
                        "with fast-access: predicate_attribute, tensor_attribute. " +
                        "Predicate, tensor and reference attributes are incompatible with fast-access.");
        builder.build();
    }

}
