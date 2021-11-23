// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi;

import com.intellij.psi.tree.IElementType;
import ai.vespa.intellij.schema.SdLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * An SdElementType.
 *
 * @author Shahar Ariel
 */
public class SdElementType extends IElementType {
    
    public SdElementType(@NotNull @NonNls String debugName) {
        super(debugName, SdLanguage.INSTANCE);
    }

}
