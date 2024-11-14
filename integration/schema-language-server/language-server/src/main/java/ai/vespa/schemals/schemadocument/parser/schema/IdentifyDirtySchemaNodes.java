package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.schemadocument.parser.IdentifyDirtyNodes;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Mark all dirty nodes as Syntax error
 */
public class IdentifyDirtySchemaNodes extends Identifier<SchemaNode> {

    private IdentifyDirtyNodes<SchemaNode> pureDirtyNodeIdentifier;

    public IdentifyDirtySchemaNodes(ParseContext context) {
		super(context);
        pureDirtyNodeIdentifier = new IdentifyDirtyNodes<SchemaNode>(context);
	}

	private String getParseExceptionMessage(ParseException exception) {

        Throwable cause = exception.getCause();

        if (cause == null) {
            return exception.getMessage();
        }

        return cause.getMessage();
    }

    public List<Diagnostic> identify(SchemaNode node) {
        List<Diagnostic> ret = new ArrayList<>();

        ParseExceptionSource parseException = node.getParseExceptionSource();
        
        if (parseException != null) {
            TokenSource tokenSource = node.getTokenSource();
            Range range = CSTUtils.getRangeFromOffsets(tokenSource, parseException.beginOffset, parseException.endOffset);
            String message = getParseExceptionMessage(parseException.parseException);
            ret.add(new SchemaDiagnostic.Builder()
                .setRange(range)
                .setMessage(message)
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }

        IllegalArgumentException illegalArgumentException = node.getIllegalArgumentException();

        if (illegalArgumentException != null) {
            ret.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage(illegalArgumentException.getMessage())
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }


        if (
            parseException == null &&
            illegalArgumentException == null
        ) {
            ret.addAll(pureDirtyNodeIdentifier.identify(node));
        }

        return ret;
    }
}
