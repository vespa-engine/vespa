// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * A general tree in the "Call Hierarchy" window.
 *
 * @author Shahar Ariel
 */
public abstract class SdCallTreeStructure extends HierarchyTreeStructure {

    protected final String myScopeType;
    protected final SdFile myFile;
    protected HashMap<String, List<PsiElement>> macrosMap;
    protected HashMap<String, HashSet<SdRankProfileDefinition>> ranksHeritageMap;
    
    public SdCallTreeStructure(Project project, PsiElement element, String currentScopeType) {
        super(project, new SdCallHierarchyNodeDescriptor(null, element, true));
        myScopeType = currentScopeType;
        myFile = (SdFile) element.getContainingFile();
        macrosMap = SdUtil.createMacrosMap(myFile);
        ranksHeritageMap = new HashMap<>();
    }
    
    @NotNull
    protected abstract HashSet<PsiElement> getChildren(@NotNull SdFunctionDefinition element);
    
    @Override
    protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
        final List<SdCallHierarchyNodeDescriptor> descriptors = new ArrayList<>();
        
        if (descriptor instanceof SdCallHierarchyNodeDescriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
            }
            boolean isCallable = SdHierarchyUtil.isExecutable(element);
            HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
            if (!isCallable || nodeDescriptor == null) {
                return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
            }
            
            HashSet<PsiElement> children = getChildren((SdFunctionDefinition) element);
            
            final HashMap<PsiElement, SdCallHierarchyNodeDescriptor> callerToDescriptorMap = new HashMap<>();
            PsiElement baseClass = PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
            
            for (PsiElement caller : children) {
                if (isInScope(baseClass, caller, myScopeType)) {
                    SdCallHierarchyNodeDescriptor callerDescriptor = callerToDescriptorMap.get(caller);
                    if (callerDescriptor == null) {
                        callerDescriptor = new SdCallHierarchyNodeDescriptor(descriptor, caller, false);
                        callerToDescriptorMap.put(caller, callerDescriptor);
                        descriptors.add(callerDescriptor);
                    }
                }
            }
        }
        return ArrayUtil.toObjectArray(descriptors);
    }

}
