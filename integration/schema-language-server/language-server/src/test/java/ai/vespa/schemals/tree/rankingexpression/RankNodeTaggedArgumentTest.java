package ai.vespa.schemals.tree.rankingexpression;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("", RankNode.findTagValueText(labelArg.getTagValueNode()));
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
        assertEquals("title", RankNode.findTagValueText(fieldArg.getTagValueNode()));
        assertTrue(new TaggedFieldArgument("name").validateArgument(fieldArg));
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
