// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * This class is used for the extension (in plugin.xml), to enable turning a line into a comment with 
 * "Code -> Comment with line comment".
 * @author shahariel
 */
public class SdCommenter implements Commenter {
    
    @Nullable
    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
    
    @Nullable
    @Override
    public String getBlockCommentPrefix() {
        return "";
    }
    
    @Nullable
    @Override
    public String getBlockCommentSuffix() {
        return null;
    }
    
    @Nullable
    @Override
    public String getCommentedBlockCommentPrefix() {
        return null;
    }
    
    @Nullable
    @Override
    public String getCommentedBlockCommentSuffix() {
        return null;
    }
    
}
