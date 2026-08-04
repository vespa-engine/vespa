package ai.vespa.schemals.tree.rankingexpression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.vespa.schemals.parser.rankingexpression.ast.IDENTIFIER;
import ai.vespa.schemals.parser.rankingexpression.ast.identifierStr;
import ai.vespa.schemals.parser.rankingexpression.ast.tagValueStr;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.TaggedFieldArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.TaggedLabelArgument;
import ai.vespa.schemals.testutils.Utils;

public class RankNodeTaggedArgumentTest {

    @Test
    void bm25TaggedLabelArgumentIsPreserved() throws Exception {
        String sd = """
            schema test {
                document test {
                    field title type string { indexing: index }
                }
                rank-profile default {
                    first-phase {
                        expression: bm25(title, label: mylabel)
                    }
                }
            }
            """;
        var context = Utils.createTestContext(sd, "test.sd");
        context.useGeneralIdentifers();
        context.useDocumentIdentifiers();
        var parseResult = SchemaDocument.parseContent(context);

        List<RankNode> roots = RankNode.createTree(parseResult.CST().get());
        RankNode bm25 = findFeature(roots, "bm25");
        assertEquals(2, bm25.getChildren().size(), () -> bm25.getChildren().toString());
        assertTrue(bm25.getChildren().get(1).isTaggedArgument(), () -> bm25.getChildren().get(1).toString());
        assertEquals("label", bm25.getChildren().get(1).getTagKey());
        assertEquals("mylabel", bm25.getChildren().get(1).getTagValue());
        var valueNode = bm25.getChildren().get(1).getTagValueNode();
        assertTrue(valueNode.isASTInstance(identifierStr.class)
                       || valueNode.isASTInstance(IDENTIFIER.class)
                       || valueNode.isASTInstance(tagValueStr.class),
                   () -> valueNode.getClassLeafIdentifierString());
        assertTrue(new TaggedLabelArgument("name").validateArgument(bm25.getChildren().get(1)));
    }

    @Test
    void singleQuotedEmptyLabelIsRejected() throws Exception {
        String sd = """
            schema test {
                document test {
                    field title type string { indexing: index }
                }
                rank-profile default {
                    first-phase {
                        expression: bm25(title, label: '')
                    }
                }
            }
            """;
        var context = Utils.createTestContext(sd, "test.sd");
        context.useGeneralIdentifers();
        context.useDocumentIdentifiers();
        var parseResult = SchemaDocument.parseContent(context);

        List<RankNode> roots = RankNode.createTree(parseResult.CST().get());
        RankNode bm25 = findFeature(roots, "bm25");
        RankNode labelArg = bm25.getChildren().get(1);
        assertEquals("", labelArg.getTagValue());
        assertTrue(!new TaggedLabelArgument("name").validateArgument(labelArg));
    }

    @Test
    void quotedFieldTagIsAccepted() throws Exception {
        String sd = """
            schema test {
                document test {
                    field title type string { indexing: index }
                }
                rank-profile default {
                    first-phase {
                        expression: bm25(field: "title", label: mylabel)
                    }
                }
            }
            """;
        var context = Utils.createTestContext(sd, "test.sd");
        context.useGeneralIdentifers();
        context.useDocumentIdentifiers();
        var parseResult = SchemaDocument.parseContent(context);
        assertEquals(0, Utils.countErrors(parseResult.diagnostics()),
                     () -> Utils.constructDiagnosticMessage(parseResult.diagnostics(), 1));

        List<RankNode> roots = RankNode.createTree(parseResult.CST().get());
        RankNode bm25 = findFeature(roots, "bm25");
        RankNode fieldArg = bm25.getChildren().get(0);
        assertEquals("field", fieldArg.getTagKey());
        assertEquals("title", fieldArg.getTagValue());
        assertTrue(new TaggedFieldArgument("name").validateArgument(fieldArg));
    }

    @Test
    void escapedQuotedLabelIsDecoded() throws Exception {
        RankNode bm25 = parseBm25("bm25(title, label: \"quote\\\"back\")");
        RankNode labelArg = bm25.getChildren().get(1);
        assertEquals("quote\"back", labelArg.getTagValue());
        assertTrue(new TaggedLabelArgument("name").validateArgument(labelArg));
    }

    @Test
    void tensorSliceIsNotATaggedArgument() throws Exception {
        String sd = """
            schema test {
                document test {
                    field t1 type tensor<float>(x[3]) { indexing: attribute }
                }
                rank-profile default {
                    first-phase {
                        expression: attribute(t1){x:0}
                    }
                }
            }
            """;
        var context = Utils.createTestContext(sd, "test.sd");
        context.useGeneralIdentifers();
        context.useDocumentIdentifiers();
        var parseResult = SchemaDocument.parseContent(context);

        List<RankNode> roots = RankNode.createTree(parseResult.CST().get());
        assertFalse(containsTaggedArgument(roots));
    }

    private static RankNode parseBm25(String expression) throws Exception {
        String sd = """
            schema test {
                document test {
                    field title type string { indexing: index }
                }
                rank-profile default {
                    first-phase {
                        expression: %s
                    }
                }
            }
            """.formatted(expression);
        var context = Utils.createTestContext(sd, "test.sd");
        context.useGeneralIdentifers();
        context.useDocumentIdentifiers();
        var parseResult = SchemaDocument.parseContent(context);
        return findFeature(RankNode.createTree(parseResult.CST().get()), "bm25");
    }

    private static boolean containsTaggedArgument(List<RankNode> nodes) {
        for (RankNode node : nodes) {
            if (node.isTaggedArgument() || containsTaggedArgument(node.getChildren())) {
                return true;
            }
        }
        return false;
    }

    private static RankNode findFeature(List<RankNode> nodes, String name) {
        for (RankNode node : nodes) {
            if (node.getType() == RankNode.RankNodeType.FEATURE
                    && node.hasSymbol()
                    && name.equals(node.getSymbol().getShortIdentifier())) {
                return node;
            }
            RankNode nested = findFeature(node.getChildren(), name);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
