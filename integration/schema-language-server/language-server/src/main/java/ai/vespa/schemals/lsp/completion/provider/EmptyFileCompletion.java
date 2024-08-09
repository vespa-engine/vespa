package ai.vespa.schemals.lsp.completion.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * EmptyFileProvider
 */
public class EmptyFileCompletion implements CompletionProvider {

	private boolean match(EventCompletionContext context) {
        DocumentManager document = context.document;

        if (document.getCurrentContent() == null || document.getCurrentContent().isBlank()) return true;

        SchemaNode rootNode = document.getRootNode();

        if (rootNode == null) return true;

        for (SchemaNode child : rootNode) {
            if (!child.isASTInstance(NL.class)) return false;
        }

        return true;
	}

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        if (!match(context)) return List.of();

        String fileName = FileUtils.schemaNameFromPath(context.document.getFileURI()); // without extension
        if (fileName == null) return List.of();

        if (context.document.getFileURI().endsWith(".profile")) {
            return new ArrayList<>() {{
                add(CompletionUtils.constructSnippet("rank-profile", "rank-profile " + fileName + " {\n\t$0\n}"));
            }};
        } else {
            return new ArrayList<>() {{
                add(CompletionUtils.constructSnippet("schema", "schema " + fileName + " {\n\tdocument " + fileName + " {\n\t\t$0\n\t}\n}", "simple schema"));
                add(CompletionUtils.constructSnippet("document", "document " + fileName + " {\n\t$0\n}"));
                // TODO: move to separate file?
                add(CompletionUtils.constructSnippet("schema", 
                            "# https://docs.vespa.ai/en/tutorials/news-1-getting-started.html\n"
                            + "schema " + fileName + " {\n"
                            + "\tdocument " + fileName + " {\n"
                            + "\t\tfield title type string {\n\t\t\tindexing: index | summary\n\t\t}\n"
                            + "\t\t\n"
                            + "\t\tfield date type int {\n\t\t\tindexing: summary | attribute\n\t\t}\n"
                            + "\t\t\n"
                            + "\t\tfield body type string {\n"
                            + "\t\t\tindexing: index | summary\n"
                            + "\t\t\tindex: enable-bm25\n"
                            + "\t\t}\n"
                            + "\t\t$0\n" // cursor position somewhat in the center
                            + "\t\t# https://docs.vespa.ai/en/tensor-user-guide.html\n"
                            + "\t\tfield embedding type tensor<float>(d0[50]) {\n"
                            + "\t\t\tindexing: attribute\n"
                            + "\t\t\tattribute {\n"
                            + "\t\t\t\tdistance-metric: dotproduct\n"
                            + "\t\t\t}\n"
                            + "\t\t}\n"
                            + "\t}\n"
                            + "\t\n"
                            + "\tfieldset default {\n"
                            + "\t\tfields: title, body\n"
                            + "\t}\n"
                            + "\t\n"
                            + "\trank-profile recommendation inherits default {\n"
                            + "\t\tfunction nearest_neighbor() {\n"
                            + "\t\t\texpression: closeness(field, embedding)\n"
                            + "\t\t}\n"
                            + "\t\t\n"
                            + "\t\tfirst-phase {\n"
                            + "\t\t\texpression: nearest_neighbor\n"
                            + "\t\t}\n"
                            + "\t\t\n"
                            + "\t\tsummary-features {\n"
                            + "\t\t\tnearest_neighbor # this can be useful to debug ranking features\n"
                            + "\t\t}\n"
                            + "\t}\n"
                            + "}", "example schema"));
            }};
        }
	}
}
