// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.config.provision.TenantName;
import com.yahoo.lang.MutableBoolean;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagDefinition;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.UnboundFlag;
import com.yahoo.vespa.flags.json.Condition;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.Rule;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author freva
 */
public class UserFlagsSerializer {
    static void toSlime(Cursor cursor, Map<FlagId, FlagData> rawFlagData,
                        Set<TenantName> authorizedForTenantNames, boolean isOperator, String userEmail) {
        FetchVector resolveVector = FetchVector.fromMap(Map.of(FetchVector.Dimension.CONSOLE_USER_EMAIL, userEmail));
        List<FlagData> filteredFlagData = Flags.getAllFlags().stream()
                // Only include flags that have CONSOLE_USER_EMAIL dimension, this should be replaced with more explicit
                // 'target' annotation if/when that is added to flag definition
                .filter(fd -> fd.getDimensions().contains(FetchVector.Dimension.CONSOLE_USER_EMAIL))
                .map(FlagDefinition::getUnboundFlag)
                .map(flag -> filteredFlagData(flag, Optional.ofNullable(rawFlagData.get(flag.id())), authorizedForTenantNames, isOperator, resolveVector))
                .toList();

        byte[] bytes = FlagData.serializeListToUtf8Json(filteredFlagData);
        SlimeUtils.copyObject(SlimeUtils.jsonToSlime(bytes).get(), cursor);
    }

    private static <T> FlagData filteredFlagData(UnboundFlag<T, ?, ?> definition, Optional<FlagData> original,
                                                 Set<TenantName> authorizedForTenantNames, boolean isOperator, FetchVector resolveVector) {
        MutableBoolean encounteredEmpty = new MutableBoolean(false);
        Optional<RawFlag> defaultValue = Optional.of(definition.serializer().serialize(definition.defaultValue()));
        // Include the original rules from flag DB and the default value from code if there is no default rule in DB
        List<Rule> rules = Stream.concat(original.stream().flatMap(fd -> fd.rules().stream()), Stream.of(new Rule(defaultValue)))
                // Exclude rules that do not match the resolveVector
                .filter(rule -> rule.partialMatch(resolveVector))
                // Re-create each rule with value explicitly set, either from DB or default from code and
                // a filtered set of conditions
                .map(rule -> new Rule(rule.getValueToApply().or(() -> defaultValue),
                        rule.conditions().stream()
                                .flatMap(condition -> filteredCondition(condition, authorizedForTenantNames, isOperator, resolveVector).stream())
                                .toList()))
                // We can stop as soon as we hit the first rule that has no conditions
                .takeWhile(rule -> !encounteredEmpty.getAndSet(rule.conditions().isEmpty()))
                .toList();

        return new FlagData(definition.id(), new FetchVector(), rules);
    }

    private static Optional<Condition> filteredCondition(Condition condition, Set<TenantName> authorizedForTenantNames,
                                                         boolean isOperator, FetchVector resolveVector) {
        // If the condition is one of the conditions that we resolve on the server, e.g. email, we do not need to
        // propagate it back to the user
        if (resolveVector.hasDimension(condition.dimension())) return Optional.empty();

        // For the other dimensions, filter the values down to an allowed subset
        switch (condition.dimension()) {
            case TENANT_ID: return valueSubset(condition, tenant -> isOperator || authorizedForTenantNames.contains(TenantName.from(tenant)));
            case APPLICATION_ID: return valueSubset(condition, appId -> isOperator || authorizedForTenantNames.stream().anyMatch(tenant -> appId.startsWith(tenant.value() + ":")));
            default: throw new IllegalArgumentException("Dimension " + condition.dimension() + " is not supported for user flags");
        }
    }

    private static Optional<Condition> valueSubset(Condition condition, Predicate<String> predicate) {
        Condition.CreateParams createParams = condition.toCreateParams();
        return Optional.of(createParams
                .withValues(createParams.values().stream().filter(predicate).toList())
                .createAs(condition.type()));
    }
}
