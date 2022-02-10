package ai.vespa.intellij.schema.psi;

import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.SdUtil;

/**
 * A function's declaration in the SD language.
 *
 * @author Shahar Ariel
 */
public interface SdFunctionDefinitionInterface extends SdDeclaration {

    default boolean isOverride() {
        String functionName = this.getName();
        SdRankProfileDefinition thisRankProfile = PsiTreeUtil.getParentOfType(this, SdRankProfileDefinition.class);
        if (thisRankProfile == null) return false;
        for (var parentProfile : SdUtil.getRankProfileParents(thisRankProfile)) {
            if (containsFunction(functionName, parentProfile))
                return true;
        }
        return false;
    }

    default boolean containsFunction(String functionName, SdRankProfileDefinition rankProfile) {
        for (var parentProfile : SdUtil.getRankProfileParents(rankProfile)) {
            if (containsFunction(functionName, parentProfile))
                return true;
        }
        return false;
    }

}
