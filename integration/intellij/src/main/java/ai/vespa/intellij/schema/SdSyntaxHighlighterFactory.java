// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * This class is used for the extension (in plugin.xml) to the class SdSyntaxHighlighter.
 *
 * @author Shahar Ariel
 */
public class SdSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
    
    @Override
    public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
        return new SdSyntaxHighlighter();
    }
    
}
