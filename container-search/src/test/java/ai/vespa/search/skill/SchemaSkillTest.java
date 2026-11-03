// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldSet;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ingrid
 */
public class SchemaSkillTest {

    @Test
    void testOverview() {
        String overview = new SchemaSkill(schemaInfo()).overview(List.of("/search/"), name -> "/skills/v1/schemas/" + name);
        assertFrontMatter(overview);
        assertTrue(overview.contains("`/search/`"), overview);

        String product = section(overview, "## Schema `product`");
        assertTrue(product.contains("Products in a web shop."), product);
        assertTrue(product.contains("`/skills/v1/schemas/product`"), product);
        assertFalse(product.contains("streaming"), product);

        String mail = section(overview, "## Schema `mail`");
        assertTrue(mail.contains("`streaming.groupname`"), mail);
        assertTrue(mail.contains("`/skills/v1/schemas/mail`"), mail);
    }

    @Test
    void testOverviewWithoutSchemas() {
        String overview = new SchemaSkill(new SchemaInfo(List.of(), List.of())).overview(List.of("/search/", "/query/"),
                                                                                           name -> "/skills/v1/schemas/" + name);
        assertFrontMatter(overview);
        assertTrue(overview.contains("`/search/`"), overview);
        assertTrue(overview.contains("`/query/`"), overview);
        assertFalse(overview.contains("## Schema"), overview);
    }

    /** The full document of one schema, as an example of the format. The other tests check what is listed, not the wording. */
    @Test
    void testSchema() {
        SchemaSkill skill = new SchemaSkill(schemaInfo());
        assertEquals("""
                     # Schema `product`

                     Products in a web shop.

                     Each document is a single product.

                     ## Fields

                     - `title` (string; index; match: text; aliases: `name`): The product title.
                     - `weight` (float; attribute): Weight in kg.
                       Not set for digital products.
                     - `embedding` (tensor<float>(x[4]); attribute): Embedding of the title.
                     - `attributes` (map<string, string>; searchable through its struct fields and by lookup)
                       - `attributes.key` (string; attribute): The attribute name.
                         Such as color or material.
                       - `attributes.by_key{key}` (lookup; key: string, value: string)
                     - `tags` (array<string>; attribute; match: word)
                     - `scores` (weightedset<int>; attribute)
                     - `brand` (reference<brand>; attribute)
                     - `size` (struct dimensions; not searchable)
                     - `packages` (array<struct dimensions>; searchable through its struct fields)
                       - `packages.width` (float; attribute)
                     - `location` (position; attribute)
                     - `internal_id` (string; not searchable)

                     ## Field sets

                     - `default`: `title`

                     ## Rank profiles

                     - `default`
                     - `semantic`: Ranks by closeness to the query embedding. Use for natural language queries.
                       - Input `query(q)` (`tensor<float>(x[4])`): The query embedding.
                         Made with the same model as the title embeddings.
                       - Input `query(boost)` (`double`)
                       - Input `query(lang)` (`string`)
                     """,
                     skill.schema("product").get());
    }

    @Test
    void testStreamingSchema() {
        String mail = new SchemaSkill(schemaInfo()).schema("mail").get();
        assertTrue(mail.startsWith("# Schema `mail`\n"), mail);
        assertTrue(mail.contains("`streaming.groupname`"), mail);
        assertTrue(mail.contains("- `subject` (string; index, attribute)"), mail);
    }

    @Test
    void testUnknownSchema() {
        assertEquals(Optional.empty(), new SchemaSkill(schemaInfo()).schema("nonexisting"));
    }

    private static void assertFrontMatter(String overview) {
        assertTrue(overview.startsWith("---\nname: " + SchemaSkill.NAME + "\ndescription: " + SchemaSkill.DESCRIPTION + "\n---\n"),
                   overview);
    }

    /** Returns the part of the given markdown from the given heading to the next heading of the same level. */
    private static String section(String markdown, String heading) {
        int start = markdown.indexOf(heading + "\n");
        assertTrue(start >= 0, "No '" + heading + "' in\n" + markdown);
        int end = markdown.indexOf("\n## ", start + heading.length());
        return end < 0 ? markdown.substring(start) : markdown.substring(start, end + 1);
    }

    static SchemaInfo schemaInfo() {
        var product = new Schema.Builder("product")
                .setExplanation("  Products in a web shop.\n\nEach document is a single product.\n")
                .add(new Field.Builder("title", "string").setIndex(true).setMatchType(Field.MatchType.TEXT).addAlias("name")
                                                         .setExplanation("The product title.").build())
                .add(new Field.Builder("weight", "float").setAttribute(true)
                                                         .setExplanation("Weight in kg.\nNot set for digital products.").build())
                .add(new Field.Builder("embedding", "tensor<float>(x[4])").setAttribute(true)
                                                                          .setExplanation("Embedding of the title.").build())
                .add(new Field.Builder("attributes", "map<string, string>").addFastMapSearch("by_key").build())
                .add(new Field.Builder("attributes.key", "string").setAttribute(true)
                                                                  .setExplanation("The attribute name.\nSuch as color or material.").build())
                .add(new Field.Builder("attributes.value", "string").build())
                .add(new Field.Builder("attributes$by_key", "string").setAttribute(true).build())
                .add(new Field.Builder("tags", "array<string>").setAttribute(true).setMatchType(Field.MatchType.WORD).build())
                .add(new Field.Builder("scores", "weightedset<int>").setAttribute(true).build())
                .add(new Field.Builder("brand", "reference<brand>").setAttribute(true).build())
                .add(new Field.Builder("size", "dimensions").build())
                .add(new Field.Builder("size.width", "float").build())
                .add(new Field.Builder("packages", "array<dimensions>").build())
                .add(new Field.Builder("packages.width", "float").setAttribute(true).build())
                .add(new Field.Builder("location", "position").build())
                .add(new Field.Builder("location_zcurve", "long").setAttribute(true).build())
                .add(new Field.Builder("internal_id", "string").build())
                .add(new FieldSet.Builder("default").addField("title").build())
                .add(new FieldSet.Builder("[document]").addField("title").addField("weight").build())
                .add(new RankProfile.Builder("default").build())
                .add(new RankProfile.Builder("semantic")
                             .setExplanation("Ranks by closeness to the query embedding. Use for natural language queries.")
                             .addInput(new RankProfile.Input("query(q)", RankProfile.InputType.fromSpec("tensor<float>(x[4])"),
                                                             Optional.empty(),
                                                             Optional.of("The query embedding.\nMade with the same model as the title embeddings.")))
                             .addInput("query(boost)", RankProfile.InputType.fromSpec("double"))
                             .addInput("query(lang)", RankProfile.InputType.fromSpec("string"))
                             .build())
                .build();
        var mail = new Schema.Builder("mail")
                .add(new Field.Builder("subject", "string").setIndex(true).setAttribute(true).build())
                .build();
        return new SchemaInfo(List.of(product, mail),
                              List.of(new Cluster.Builder("products").addSchema("product").build(),
                                      new Cluster.Builder("mail").addSchema("mail").setStreaming(true).build()));
    }

}
