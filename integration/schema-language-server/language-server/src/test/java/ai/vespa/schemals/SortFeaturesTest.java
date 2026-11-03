package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.common.semantictokens.CommonSemanticTokens;
import ai.vespa.schemals.lsp.schema.completion.SchemaCompletion;
import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocument.ParseResult;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;
import ai.vespa.schemals.schemadocument.resolvers.ValidateSortFeatures;
import ai.vespa.schemals.testutils.TestLogger;
import ai.vespa.schemals.testutils.TestSchemaDiagnosticsHandler;
import ai.vespa.schemals.testutils.TestSchemaMessageHandler;
import ai.vespa.schemals.testutils.TestSchemaProgressHandler;
import ai.vespa.schemals.testutils.Utils;

class SortFeaturesTest {
    private static final String PROFILE = """
        schema test {
            document test {
                field title type string {
                    indexing: index | summary
                    index: enable-bm25
                }
                field price type int {
                    indexing: attribute | summary
                }
            }
            rank-profile parent {
                function key() {
                    expression: 1.0
                }
                function with_arg(x) {
                    expression: x
                }
                function title_bm25() {
                    expression: bm25(title)
                }
            }
            rank-profile child inherits parent {
                %s
            }
        }
        """;

    @Test
    void keywordSnippetsAndSemanticToken() throws Exception {
        Fixture keywords = new Fixture(PROFILE.formatted("§"));
        assertEquals(List.of("sort-features {\n\t$0\n}", "sort-features: $0"), keywords.completions().stream()
            .filter(item -> item.getLabel().equals("sort-features")).map(CompletionItem::getInsertText).toList());

        CommonSemanticTokens.getSemanticTokensRegistrationOptions();
        Fixture fixture = new Fixture(PROFILE.formatted("sort-features: nativeRank"));
        var tokens = SchemaSemanticTokens.getSemanticTokens(new EventDocumentContext(fixture.scheduler, fixture.index,
            fixture.messages, fixture.document.getVersionedTextDocumentIdentifier())).getData();
        List<String> lines = fixture.content.lines().toList();
        int line = 0;
        int character = 0;
        int sortKeywords = 0;
        for (int i = 0; i < tokens.size(); i += 5) {
            character = tokens.get(i) == 0 ? character + tokens.get(i + 1) : tokens.get(i + 1);
            line += tokens.get(i);
            if (lines.get(line).substring(character).startsWith("sort-features")) {
                assertEquals("sort-features".length(), tokens.get(i + 2));
                assertEquals(CommonSemanticTokens.getType(SemanticTokenTypes.Keyword), tokens.get(i + 3));
                sortKeywords++;
            }
        }
        assertEquals(1, sortKeywords);
    }

    @Test
    void inListCompletionInsertsBareZeroArgNames() throws Exception {
        for (String declaration : List.of("sort-features: §\n", "sort-features {\n        §\n    }",
                                          "sort-features: t§\n", "sort-features { t§ }",
                                          "sort-features { §}", "sort-features { k§}")) {
            Fixture fixture = new Fixture(PROFILE.formatted(declaration));
            List<CompletionItem> items = fixture.completions();
            for (String name : List.of("title_bm25", "nativeRank", "key")) {
                assertTrue(items.stream().anyMatch(item -> name.equals(item.getLabel()) && name.equals(item.getInsertText())),
                           declaration + " should complete bare " + name + ": " + labels(items));
            }
            assertFalse(items.stream().anyMatch(item -> "with_arg".equals(item.getLabel()) || "bm25".equals(item.getLabel())),
                        "Parameterized names must be absent from " + declaration + ": " + labels(items));
        }

        Fixture overridden = new Fixture(PROFILE.formatted("""
            function key(x) {
                expression: x
            }
            sort-features: §
            """));
        assertFalse(overridden.completions().stream().anyMatch(item -> "key".equals(item.getLabel())),
                    "A child that overrides key() with key(x) must not complete key");

        Fixture dotted = new Fixture(PROFILE.formatted("sort-features: nativeRank.§\n"), ".");
        assertTrue(dotted.completions().isEmpty(), "Output selectors are illegal in sort-features");
    }

    @Test
    void genericSnippetsUnchangedOutsideSortFeatures() throws Exception {
        for (String declaration : List.of("match-features: k§ey\n", "first-phase {\n        expression: k§ey\n    }")) {
            Fixture fixture = new Fixture(PROFILE.formatted(declaration));
            assertTrue(fixture.completions().stream().anyMatch(item -> "key".equals(item.getLabel())
                    && item.getInsertText() != null && item.getInsertText().startsWith("key(")),
                       declaration + " should still insert key(): " + labels(fixture.completions()));
        }
    }

    @Test
    void identifierGateIsLocalAndOnePerEntry() throws Exception {
        String schema = """
            schema test {
                document test {
                    field title type string {
                        indexing: index | summary
                        index: enable-bm25
                    }
                    field price type int {
                        indexing: attribute | summary
                    }
                }
                rank-profile p {
                    function key() {
                        expression: 1.0
                    }
                    match-features { attribute(price) }
                    summary-features { attribute(price) }
                    first-phase {
                        expression: attribute(price)
                    }
                    sort-features {
                        attribute(price)
                        key()
                        key
                        nosuchfn
                    }
                }
            }
            """;
        ParseResult mixed = new SchemaParserTest().parseString(schema, "test.sd");
        List<Diagnostic> identifierErrors = identifierGate(mixed.diagnostics());
        assertEquals(2, identifierErrors.size(), Utils.constructDiagnosticMessage(mixed.diagnostics(), 1));
        assertEquals(List.of("attribute(price)", "key()"),
                     identifierErrors.stream().map(d -> textAt(schema, d.getRange())).toList());

        assertTrue(mixed.diagnostics().stream().anyMatch(d -> d.getMessage().contains("Undefined symbol")),
                   "Misspelled bare name must still yield Undefined symbol"
                   + Utils.constructDiagnosticMessage(mixed.diagnostics(), 1));
        assertTrue(identifierErrors.stream().noneMatch(d -> "nosuchfn".equals(textAt(schema, d.getRange()))),
                   "A misspelled bare identifier must not get the identifier-gate diagnostic");

        String legalElsewhere = """
            schema test {
                document test {
                    field title type string {
                        indexing: index | summary
                        index: enable-bm25
                    }
                    field price type int {
                        indexing: attribute | summary
                    }
                }
                rank-profile p {
                    match-features { attribute(price) }
                    summary-features { attribute(price) }
                    first-phase {
                        expression: attribute(price)
                    }
                }
            }
            """;
        ParseResult elsewhere = new SchemaParserTest().parseString(legalElsewhere, "test.sd");
        assertEquals(0, identifierGate(elsewhere.diagnostics()).size(),
                     Utils.constructDiagnosticMessage(elsewhere.diagnostics(), 1));
    }

    private static List<Diagnostic> identifierGate(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
            .filter(d -> d.getMessage() != null && d.getMessage().startsWith(ValidateSortFeatures.IDENTIFIER_ERROR_PREFIX))
            .toList();
    }

    private static String textAt(String content, Range range) {
        return content.substring(offset(content, range.getStart()), offset(content, range.getEnd()));
    }

    private static int offset(String content, Position position) {
        int offset = 0;
        for (int i = 0; i < position.getLine(); i++) {
            offset = content.indexOf('\n', offset) + 1;
        }
        return offset + position.getCharacter();
    }

    private static List<String> labels(List<CompletionItem> items) {
        return items.stream().map(item -> item.getLabel() + "->" + item.getInsertText()).toList();
    }

    private static class Fixture {
        final TestSchemaMessageHandler messages = new TestSchemaMessageHandler();
        final TestLogger logger = new TestLogger(messages);
        final SchemaIndex index = new SchemaIndex(logger);
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger,
            new TestSchemaDiagnosticsHandler(diagnostics), index, messages, new TestSchemaProgressHandler());
        final String content;
        final Position position;
        final DocumentManager document;
        final String trigger;

        Fixture(String markedContent) {
            this(markedContent, null);
        }

        Fixture(String markedContent, String trigger) {
            SchemaLanguageServer.serverPath = Paths.get("target");
            this.trigger = trigger;
            int marker = markedContent.indexOf('§');
            String prefix = marker < 0 ? markedContent : markedContent.substring(0, marker);
            position = new Position((int) prefix.chars().filter(c -> c == '\n').count(),
                prefix.length() - prefix.lastIndexOf('\n') - 1);
            content = markedContent.replace("§", "");
            String uri = new File("src/test/sdfiles/single/test.sd").toURI().toString();
            scheduler.openDocument(new TextDocumentItem(uri, "vespaSchema", 0, content));
            assertTrue(logger.getErrorMessages().isEmpty(), logger.getErrorMessages().toString());
            document = scheduler.getDocument(uri);
        }

        List<CompletionItem> completions() throws Exception {
            return SchemaCompletion.getCompletionItems(new EventCompletionContext(scheduler, index, messages,
                document.getVersionedTextDocumentIdentifier(), position, trigger), System.err);
        }
    }
}
