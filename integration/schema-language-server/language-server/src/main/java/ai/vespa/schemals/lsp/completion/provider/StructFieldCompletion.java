package ai.vespa.schemals.lsp.completion.provider;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * StructFieldProvider
 * Responsible for providing the "struct-field" suggestion itself 
 * (not necessarily stuff inside struct-field"). A bit confusing because struct-fields can be nested.
 * The rest of the suggestions for struct-field body is in {@link BodyKeywordCompletion} because they are static.
 */
public class StructFieldCompletion implements CompletionProvider {

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        if (!(context.document instanceof SchemaDocument)) return List.of();

        SchemaNode lastCleanNode = CSTUtils.getLastCleanNode(context.document.getRootNode(), context.startOfWord());
        if (lastCleanNode == null || !lastCleanNode.isASTInstance(NL.class) || lastCleanNode.getParent() == null) return List.of();
        SchemaNode parent = lastCleanNode.getParent();

        if (parent.isASTInstance(openLbrace.class)) parent = parent.getParent();
        if (!parent.isASTInstance(fieldElm.class) && !parent.isASTInstance(structFieldElm.class)) return List.of();

        SchemaNode fieldDefinitionNode = parent.get(1);
        if (!fieldDefinitionNode.hasSymbol()) return List.of();

        Optional<Symbol> definition = context.schemaIndex.getFirstSymbolDefinition(fieldDefinitionNode.getSymbol());
        if (definition.isEmpty()) return List.of();

        Optional<Symbol> structDefinition = context.schemaIndex.fieldIndex().findFieldStructDefinition(definition.get());
        if (structDefinition.isEmpty()) return List.of();

        List<Symbol> fieldsInStruct = context.schemaIndex.listSymbolsInScope(structDefinition.get(), EnumSet.of(SymbolType.FIELD, SymbolType.MAP_KEY, SymbolType.MAP_VALUE));
        if (fieldsInStruct.isEmpty()) return List.of(CompletionUtils.constructBasic("struct-field")); // just simple keyword completion if we cannot suggest fields

        String choiceString = String.join(",", fieldsInStruct.stream().map(symbol -> symbol.getShortIdentifier()).toList());

        // Ugly edge case: MAP_VALUE is not defined if value is a struct
        // the result in that case is a list with 1 element which is a MAP_KEY
        if (fieldsInStruct.size() == 1 && fieldsInStruct.get(0).getType() == SymbolType.MAP_KEY) {
            choiceString += ",value";
        }

        choiceString = "${1|" + choiceString + "|}";
        return List.of(CompletionUtils.constructSnippet("struct-field", "struct-field " + choiceString + " {\n\t$0\n}"));
	}
}
