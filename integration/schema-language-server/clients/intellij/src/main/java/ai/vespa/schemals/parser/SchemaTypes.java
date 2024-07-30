package ai.vespa.schemals.parser;

import ai.vespa.schemals.intellij.SchemaLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SchemaTypes {

    private static class SchemaElementType extends IElementType {
        public SchemaElementType(@NonNls @NotNull String debugName) {
            super(debugName, SchemaLanguage.INSTANCE);
        }
    }

    public final static IElementType KEYWORD = new SchemaElementType("keyword");
    public final static IElementType NONE = new SchemaElementType("none");
    public final static IElementType TYPE = new SchemaElementType("type");
    public final static IElementType NUMBER = new SchemaElementType("number");
    public final static IElementType COMMENT = new SchemaElementType("comment");
    public final static IElementType IDENTIFIER = new SchemaElementType("identifier");
    public final static IElementType STRING = new SchemaElementType("string");
    public final static IElementType BOOLEAN = new SchemaElementType("boolean");
}
