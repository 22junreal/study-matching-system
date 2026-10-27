package com.studymatching.common.config;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import java.time.LocalTime;
public class MinuteTimeDeserializer extends ValueDeserializer<LocalTime> {
    @Override public LocalTime deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) return (LocalTime) context.handleUnexpectedToken(LocalTime.class, parser);
        String value = parser.getString();
        if (!value.matches("([01][0-9]|2[0-3]):[0-5][0-9]")) {
            throw context.weirdStringException(value, LocalTime.class, "Expected HH:mm, 00:00 through 23:59");
        }
        return LocalTime.parse(value);
    }
}
