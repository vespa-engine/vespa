package ai.vespa.schemals.lsp.schema.completion.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;


public class CompletionUtils {
    public static CompletionItem constructSnippet(String label, String snippet, String detail) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setKind(CompletionItemKind.Snippet);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setInsertText(snippet);
        item.setInsertTextMode(InsertTextMode.AdjustIndentation);
        item.setDetail(detail);
        return item;
    }

    public static CompletionItem constructSnippet(String label, String snippet) {
        return constructSnippet(label, snippet, "");
    }

    public static CompletionItem constructType(String label) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setKind(CompletionItemKind.TypeParameter);
        return item;
    }

    public static CompletionItem constructBasic(String label) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        return item;
    }

    public static CompletionItem constructBasic(String label, String detail) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setDetail(detail);
        return item;
    }

    public static CompletionItem constructBasicDeprecated(String label) {
        CompletionItem item = constructBasic(label);
        // This is deprecated (lol): item.setDeprecated(deprecated);
        item.setTags(List.of(CompletionItemTag.Deprecated));
        return item;
    }

    public static CompletionItem withSortingPrefix(String prefix, CompletionItem item) {
        item.setSortText(prefix + item.getLabel());
        return item;
    }

    public static CompletionItem constructFunction(String name, String signature, String longIdentifier) {
        CompletionItem item = new CompletionItem(name);
        item.setKind(CompletionItemKind.Function);
        var labelDetails = new CompletionItemLabelDetails();
        labelDetails.setDetail(signature);
        labelDetails.setDescription(longIdentifier);
        item.setLabelDetails(labelDetails);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setInsertTextMode(InsertTextMode.AdjustIndentation);

        signature = signature.trim();
        if (signature.length() > 2 && signature.startsWith("(") && signature.endsWith(")")) {
            String[] args = signature.substring(1, signature.length() - 1).split(",");
            List<String> snippetTemplates = new ArrayList<>();
            for (int i = 0; i < args.length; ++i) {
                snippetTemplates.add("${" + (i+1) + ":" + args[i] + "}");
            }
            signature = "(" + String.join(", ", snippetTemplates) + ")";
        }        
        item.setInsertText(name + signature);
        return item;
    }
}
