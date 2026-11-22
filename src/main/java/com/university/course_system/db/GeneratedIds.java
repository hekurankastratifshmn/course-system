package com.university.course_system.db;

import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;

public final class GeneratedIds {

    private GeneratedIds() {
    }

    public static int from(KeyHolder keys) {
        if (keys.getKeyList().isEmpty()) {
            throw new IllegalStateException("MySQL did not return the new id");
        }
        Map<String, Object> row = keys.getKeyList().getFirst();
        Object value = row.get("GENERATED_KEY");
        if (value == null) {
            value = row.get("id");
        }
        if (value == null && !row.isEmpty()) {
            value = row.values().iterator().next();
        }
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("MySQL did not return the new id");
        }
        return number.intValue();
    }

}
