package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.tensor.TensorType;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument that has to be a valid cell type of a tensor. For example "float" or "int8".
 */
public class TensorTypeArgument implements Argument {

    private String displayStr = "type";
    private String errorMessage = "";

    public TensorTypeArgument() {}

    public TensorTypeArgument(String displayStr) { 
        this.displayStr = displayStr;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        String typeString = node.getSchemaNode().getText().trim();
        try {
            TensorType.Value.fromId(typeString);
            return true;
        } catch (IllegalArgumentException ex) {
            this.errorMessage = ex.getMessage();
            return false;
        }
    }

    @Override
    public int getStrictness() {
        return 2;
    }

    @Override
    public String displayString() {
        return displayStr;
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        Optional<Diagnostic> err = Optional.empty();

        if (!validateArgument(node)) {
            err = Optional.of(new SchemaDiagnostic.Builder()
                    .setRange(node.getRange())
                    .setMessage(this.errorMessage)
                    .setSeverity(DiagnosticSeverity.Error)
                    .setCode(DiagnosticCode.INVALID_TYPE)
                    .build());
        }

        ArgumentUtils.modifyNodeSymbol(context, node, err.isPresent() ? null : SymbolType.TENSOR_CELL_VALUE_TYPE, SymbolStatus.BUILTIN_REFERENCE);
        return err;
    }
}
