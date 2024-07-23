package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * EventQuickfixContext
 */
public class EventCodeActionContext extends EventPositionContext {

    public final Range range;
    public final List<Diagnostic> diagnostics;
    public final List<String> codeActionKinds;

	public EventCodeActionContext(PrintStream logger, SchemaDocumentScheduler scheduler, SchemaIndex schemaIndex,
			SchemaMessageHandler messageHandler, TextDocumentIdentifier documentIdentifier, Range range, List<Diagnostic> quickfixDiagnostics, List<String> onlyKinds) {
		super(logger, scheduler, schemaIndex, messageHandler, documentIdentifier, range.getStart());
        this.range = range;
        this.diagnostics = quickfixDiagnostics;
        this.codeActionKinds = new ArrayList<>();

        if (onlyKinds == null) {
            this.codeActionKinds.add(CodeActionKind.SourceOrganizeImports);
            this.codeActionKinds.add(CodeActionKind.QuickFix);
            this.codeActionKinds.add(CodeActionKind.Empty);
            this.codeActionKinds.add(CodeActionKind.Source);
            this.codeActionKinds.add(CodeActionKind.Refactor);
            this.codeActionKinds.add(CodeActionKind.SourceFixAll);
            this.codeActionKinds.add(CodeActionKind.RefactorInline);
            this.codeActionKinds.add(CodeActionKind.RefactorExtract);
            this.codeActionKinds.add(CodeActionKind.RefactorRewrite);
        } else {
            this.codeActionKinds.addAll(onlyKinds);
        }
	}
}
