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

/**
 * @author jonmv
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@ExtendWith(DisabledInInstancesCondition.class)
public @interface DisabledInInstances {

    /** One or more instances that this should be disabled in. */
    String[] value();

}

class DisabledInInstancesCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<DisabledInInstances> annotation = AnnotationUtils.findAnnotation(context.getElement(), DisabledInInstances.class);
        if (annotation.isEmpty())
            return ConditionEvaluationResult.enabled(DisabledInInstances.class.getSimpleName() + " is not present");

        List<String> disablingInstances = List.of(annotation.get().value());
        String thisInstance = TestRuntime.get().application().instance();
        String reason = "Disabled in: %s. Current instance: %s.".formatted(disablingInstances.isEmpty() ? "no instances" : "instances " + String.join(", ", disablingInstances), thisInstance);
        return disablingInstances.contains(thisInstance) ? ConditionEvaluationResult.disabled(reason) : ConditionEvaluationResult.enabled(reason);
    }

}