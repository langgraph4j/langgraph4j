package com.agui.json.mixins;

import com.agui.community.core.message.Role;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public interface EnumWithValueMixin {

    @JsonValue
    String value();

    @JsonCreator
    static Role fromValue(String value) {
        throw new UnsupportedOperationException(); // body never called — Jackson only reads the annotation
    }
}