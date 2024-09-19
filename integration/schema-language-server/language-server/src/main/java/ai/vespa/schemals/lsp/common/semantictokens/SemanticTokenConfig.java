package ai.vespa.schemals.lsp.common.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.SemanticTokenModifiers;

public class SemanticTokenConfig {
    
    static public final List<String> tokenModifiers = new ArrayList<>() {{
        add(SemanticTokenModifiers.Definition);
        add(SemanticTokenModifiers.Readonly);
        add(SemanticTokenModifiers.DefaultLibrary);
    }};
}
