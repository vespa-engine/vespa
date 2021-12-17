// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.lang.Commenter;

/**
 * This class is used for the extension (in plugin.xml), to enable turning a line into a comment with 
 * "Code -> Comment with line comment".
 *
 * @author Shahar Ariel
 */
public class SdCommenter implements Commenter {
    
    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
    
    @Override
    public String getBlockCommentPrefix() {
        return "";
    }
    
    @Override
    public String getBlockCommentSuffix() {
        return null;
    }
    
    @Override
    public String getCommentedBlockCommentPrefix() {
        return null;
    }
    
    @Override
    public String getCommentedBlockCommentSuffix() {
        return null;
    }
    
}
