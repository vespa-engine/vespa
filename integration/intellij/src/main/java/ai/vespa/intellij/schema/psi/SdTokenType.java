// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi;

import com.intellij.psi.tree.IElementType;
import ai.vespa.intellij.schema.SdLanguage;

/**
 * An SdTokenType.
 *
 * @author Shahar Ariel
 */
public class SdTokenType extends IElementType {
    
    public SdTokenType(String debugName) {
        super(debugName, SdLanguage.INSTANCE);
    }
    
    @Override
    public String toString() {
        return "SdTokenType." + super.toString();
    }

}
