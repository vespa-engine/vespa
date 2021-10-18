package org.intellij.sdk.language;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * This class enables turning a line into a comment with "Code -> Comment with line comment"
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
