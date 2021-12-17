// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.psi.SdDocumentAnnotationDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentStructDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentSummaryDefinition;
import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdSchemaAnnotationDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used for the presentation of an element in the "Structure View" window.
 *
 * @author Shahar Ariel
 */
public class SdStructureViewElement implements StructureViewTreeElement, SortableTreeElement {
    
    private final NavigatablePsiElement myElement;
    
    public SdStructureViewElement(NavigatablePsiElement element) {
        this.myElement = element;
    }
    
    @Override
    public Object getValue() {
        return myElement;
    }
    
    @Override
    public void navigate(boolean requestFocus) {
        myElement.navigate(requestFocus);
    }
    
    @Override
    public boolean canNavigate() {
        return myElement.canNavigate();
    }
    
    @Override
    public boolean canNavigateToSource() {
        return myElement.canNavigateToSource();
    }
    
    @Override
    public String getAlphaSortKey() {
        String name = myElement.getName();
        return name != null ? name : "";
    }
    
    @Override
    public ItemPresentation getPresentation() {
        ItemPresentation presentation = myElement.getPresentation();
        return presentation != null ? presentation : new PresentationData();
    }
    
    @Override
    public TreeElement[] getChildren() {
        List<PsiElement> children = new ArrayList<>();
        if (myElement instanceof SdFile) {
            children = SdUtil.findSchemaChildren(myElement);
        } else if (myElement instanceof SdDocumentDefinition) {
            children = SdUtil.findDocumentChildren(myElement);
        } else if (myElement instanceof SdDocumentStructDefinition) {
            children = SdUtil.findDocumentStructChildren(myElement);
        } else if (myElement instanceof SdRankProfileDefinition) {
            children = SdUtil.findRankProfileChildren(myElement);
        } else if (myElement instanceof SdDocumentSummaryDefinition) {
            children = SdUtil.findDocumentSummaryChildren(myElement);
        } else if (myElement instanceof SdSchemaAnnotationDefinition ||
                   myElement instanceof SdDocumentAnnotationDefinition) {
            children = SdUtil.findAnnotationChildren(myElement);
        }
        
        List<TreeElement> treeElements = new ArrayList<>(children.size());
        for (PsiElement child : children) {
            treeElements.add(new SdStructureViewElement((NavigatablePsiElement) child));
        }
        return treeElements.toArray(new TreeElement[0]);
    }

}
