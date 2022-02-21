package ai.vespa.intellij.schema.psi;

import ai.vespa.intellij.schema.model.RankProfile;
import com.intellij.psi.util.PsiTreeUtil;

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
        for (var parentProfile : new RankProfile(thisRankProfile, null).parents().values()) {
            if (containsFunction(functionName, parentProfile))
                return true;
        }
        return false;
    }

    default boolean containsFunction(String functionName, RankProfile rankProfile) {
        if (rankProfile.definedFunctions().containsKey(functionName))
            return true;
        for (var parentProfile : rankProfile.parents().values()) {
            if (containsFunction(functionName, parentProfile))
                return true;
        }
        return false;
    }

}
