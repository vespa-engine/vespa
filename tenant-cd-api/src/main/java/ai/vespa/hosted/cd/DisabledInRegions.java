package ai.vespa.hosted.cd;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author jonmv
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DisabledInRegionsCondition.class)
public @interface DisabledInRegions {

    /** One or more regions that this should be disabled in. */
    String[] value();

}

class DisabledInRegionsCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<DisabledInRegions> annotation = AnnotationUtils.findAnnotation(context.getElement(), DisabledInRegions.class);
        if (annotation.isEmpty())
            return ConditionEvaluationResult.enabled(DisabledInRegions.class.getSimpleName() + " is not present");

        List<String> disablingRegions = List.of(annotation.get().value());
        String thisRegion = TestRuntime.get().application().instance();
        String reason = "Disabled in: %s. Current region: %s.".formatted(disablingRegions.isEmpty() ? "no regions" : "regions " + String.join(", ", disablingRegions), thisRegion);
        return disablingRegions.contains(thisRegion) ? ConditionEvaluationResult.disabled(reason) : ConditionEvaluationResult.enabled(reason);
    }

}