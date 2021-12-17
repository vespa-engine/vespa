// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

/**
 * This class is used for the extension (in plugin.xml) to the class SdStructureViewModel. It make the IDE use our 
 * plugin's code when creating the "Structure View" window.
 *
 * @author Shahar Ariel
 */
public class SdStructureViewFactory implements PsiStructureViewFactory {

    public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @Override
            public StructureViewModel createStructureViewModel(Editor editor) {
                return new SdStructureViewModel(psiFile);
            }
        };
    }

}
