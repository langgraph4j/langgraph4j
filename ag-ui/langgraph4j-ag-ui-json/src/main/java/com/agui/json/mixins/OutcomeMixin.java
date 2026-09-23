package com.agui.json.mixins;

import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin for the polymorphic {@code RunOutcome} hierarchy.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SuccessOutcome.class, name = "success"),
        @JsonSubTypes.Type(value = InterruptOutcome.class, name = "interrupt")
})
public interface OutcomeMixin {
}
