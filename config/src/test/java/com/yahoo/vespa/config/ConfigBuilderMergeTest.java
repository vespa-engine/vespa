// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.foo.ArraytypesConfig;
import com.yahoo.config.subscription.ConfigInstanceUtil;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.StructtypesConfig;
import com.yahoo.foo.MaptypesConfig;
import org.junit.Test;

import java.util.Arrays;

import static com.yahoo.foo.MaptypesConfig.Innermap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * SEO keywords: test override() on builders. overrideTest, testOverride
 *
 * @author Ulf Lilleengen
 */
public class ConfigBuilderMergeTest {

    private SimpletypesConfig.Builder createSimpleBuilder(String s, int i, long l, double d, boolean b) {
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        builder.stringval(s);
        builder.intval(i);
        builder.longval(l);
        builder.doubleval(d);
        builder.boolval(b);
        return builder;
    }

    private ArraytypesConfig.Builder createArrayBuilder(String [] strings) {
        ArraytypesConfig.Builder builder = new ArraytypesConfig.Builder();
        for (String str : strings) {
            builder.stringarr(str);
        }
        return builder;
    }

    private StructtypesConfig.Builder createSimpleStructBuilder(String name, String gender, String [] emails) {
        StructtypesConfig.Builder builder = new StructtypesConfig.Builder();
        StructtypesConfig.Simple.Builder simpleBuilder = new StructtypesConfig.Simple.Builder();
        simpleBuilder.name(name);
        simpleBuilder.gender(StructtypesConfig.Simple.Gender.Enum.valueOf(gender));
        simpleBuilder.emails(Arrays.asList(emails));
        builder.simple(simpleBuilder);
        return builder;
    }

    @Test
    public void require_that_simple_fields_are_overwritten_on_merge() {
        SimpletypesConfig.Builder b1 = createSimpleBuilder("foo", 2, 5, 4.3, false);
        SimpletypesConfig.Builder b2 = createSimpleBuilder("bar", 3, 6, 3.3, true);
        ConfigInstanceUtil.setValues(b1, b2);
        SimpletypesConfig c1 = new SimpletypesConfig(b1);
        SimpletypesConfig c2 = new SimpletypesConfig(b2);
        assertThat(c1, is(c2));
    }

    @Test
    public void require_that_arrays_are_appended_on_merge() {
        ArraytypesConfig.Builder b1 = createArrayBuilder(new String[] { "foo", "bar" });
        ArraytypesConfig.Builder b2 = createArrayBuilder(new String[] { "baz", "bim" });

        ConfigInstanceUtil.setValues(b1, b2);
        ArraytypesConfig c1 = new ArraytypesConfig(b1);
        assertThat(c1.stringarr().size(), is(4));
        assertThat(c1.stringarr(0), is("foo"));
        assertThat(c1.stringarr(1), is("bar"));
        assertThat(c1.stringarr(2), is("baz"));
        assertThat(c1.stringarr(3), is("bim"));

        ArraytypesConfig c2 = new ArraytypesConfig(b2);
        assertThat(c2.stringarr(0), is("baz"));
        assertThat(c2.stringarr(1), is("bim"));
    }

    @Test
    public void require_that_struct_fields_are_overwritten() {
        String name1 = "foo";
        String gender1 = "MALE";
        String[] emails1 = {"foo@bar", "bar@foo"};
        String name2 = "bar";
        String gender2 = "FEMALE";
        String[] emails2 = {"foo@bar", "bar@foo"};
        StructtypesConfig.Builder b1 = createSimpleStructBuilder(name1, gender1, emails1);
        StructtypesConfig.Builder b2 = createSimpleStructBuilder(name2, gender2, emails2);
        ConfigInstanceUtil.setValues(b1, b2);
        StructtypesConfig c1 = new StructtypesConfig(b1);
        assertThat(c1.simple().name(),  is(name2));
        assertThat(c1.simple().gender().toString(),  is(gender2));
        assertThat(c1.simple().emails(0), is(emails2[0]));
        assertThat(c1.simple().emails(1), is(emails2[1]));
    }

    @Test
    public void source_map_is_copied_into_destination_map_on_merge() {
        MaptypesConfig.Builder destination = new MaptypesConfig.Builder()
                .intmap("one", 1)
                .innermap("first", new Innermap.Builder()
                .foo(1));

        MaptypesConfig.Builder source = new MaptypesConfig.Builder()
                .intmap("two", 2)
                .innermap("second", new Innermap.Builder()
                .foo(2));

        ConfigInstanceUtil.setValues(destination, source);

        MaptypesConfig config = new MaptypesConfig(destination);
        assertThat(config.intmap("one"), is(1));
        assertThat(config.intmap("two"), is(2));
        assertThat(config.innermap("first").foo(), is(1));
        assertThat(config.innermap("second").foo(), is(2));
    }

}
