package ai.vespa.schemals.lsp.completion.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * EmptyFileProvider
 */
public class EmptyFileProvider implements CompletionProvider {

	private boolean match(EventPositionContext context) {
        DocumentManager document = context.document;

        if (document.getCurrentContent() == null || document.getCurrentContent().isBlank()) return true;

        SchemaNode rootNode = document.getRootNode();

        if (rootNode == null) return true;

        if (rootNode.size() == 0) return true;
        return false;
	}

	@Override
	public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        if (!match(context)) return List.of();

        String fileName = FileUtils.schemaNameFromPath(context.document.getFileURI()); // without extension
        if (fileName == null) return new ArrayList<>();

        if (context.document.getFileURI().endsWith(".profile")) {
            return new ArrayList<>() {{
                add(CompletionUtils.constructSnippet("rank-profile", "rank-profile " + fileName + " {\n\t$0\n}"));
            }};
        } else {
            return new ArrayList<>() {{
                add(CompletionUtils.constructSnippet("schema", "schema " + fileName + " {\n\tdocument " + fileName + " {\n\t\t$0\n\t}\n}"));
                add(CompletionUtils.constructSnippet("document", "document " + fileName + " {\n\t$0\n}"));
            }};
        }
	}
}