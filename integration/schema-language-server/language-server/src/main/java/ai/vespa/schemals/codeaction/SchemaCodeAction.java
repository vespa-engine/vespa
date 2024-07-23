package ai.vespa.schemals.codeaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.codeaction.provider.CodeActionProvider;
import ai.vespa.schemals.codeaction.provider.QuickFixProvider;
import ai.vespa.schemals.context.EventCodeActionContext;

/**
 * SchemaCodeAction
 */
public class SchemaCodeAction {

    private static Map<String, CodeActionProvider> providers = new HashMap<>() {{
        put(CodeActionKind.QuickFix, new QuickFixProvider());
    }};

    public static List<Either<Command, CodeAction>> provideActions(EventCodeActionContext context) {

        List<Either<Command, CodeAction>> result = new ArrayList<>();

        for (String actionKind : context.codeActionKinds) {
            CodeActionProvider provider = providers.get(actionKind);
            if (provider == null) continue;
            result.addAll(provider.getActions(context));
        }

        return result;
    }

    public static CodeAction resolveAction(CodeAction unresolved) {
        return null;
    }
}
