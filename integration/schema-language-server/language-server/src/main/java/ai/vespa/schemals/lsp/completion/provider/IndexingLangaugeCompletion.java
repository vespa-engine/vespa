package ai.vespa.schemals.lsp.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.ast.COLON;
import ai.vespa.schemals.parser.ast.INDEXING;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IndexingLangaugeProvider
 */
public class IndexingLangaugeCompletion implements CompletionProvider {

    private boolean matchCommon(EventCompletionContext context) {
        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;
        SchemaNode last = CSTUtils.getLastCleanNode(context.document.getRootNode(), searchPos);

        if (last.isASTInstance(INDEXING.class)) return true;
        if (last.isASTInstance(COLON.class) && last.getPreviousSibling() != null && last.getPreviousSibling().isASTInstance(INDEXING.class))return true;

        return false;
    }

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        if (matchCommon(context)) {
            return List.of(
                // The sorting prefix tells the client how to prioritize the different suggestions
                CompletionUtils.withSortingPrefix("a", CompletionUtils.constructBasic("attribute")),
                CompletionUtils.withSortingPrefix("a", CompletionUtils.constructBasic("index")),
                CompletionUtils.withSortingPrefix("a", CompletionUtils.constructBasic("summary")),
                CompletionUtils.withSortingPrefix("b", CompletionUtils.constructBasic("input")),
                CompletionUtils.withSortingPrefix("b", CompletionUtils.constructBasic("set_language")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("embed")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("binarize")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("hash")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_array")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_byte")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_double")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_float")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_int")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_long")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_bool")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_pos")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_string")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_uri")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_wset")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("to_epoch_second")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("base64decode")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("base64encode")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("echo")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("for_each")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("get_field")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("get_var")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("hex_decode")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("hex_encode")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("hostname")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("if")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("else")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("input")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("join")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("lowercase")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("ngram")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("normalize")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("now")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("random")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("sub")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("select_input")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("set_language")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("set_var")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("substring")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("split")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("summary")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("switch")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("tokenize")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("trim")),
                CompletionUtils.withSortingPrefix("c", CompletionUtils.constructBasic("uri")),
                CompletionUtils.withSortingPrefix("z", CompletionUtils.constructBasicDeprecated("flatten"))
            );
        }
        return List.of();
	}
}
