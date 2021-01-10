package com.platformlib.plugins.gradle.wrapper.utility;

import groovy.lang.Closure;
import org.gradle.api.Task;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PlatformLibGradleWrapperUtility {
    private PlatformLibGradleWrapperUtility() {
    }

    public static boolean callBooleanClosure(final Closure closure) {
        final Object result = closure.call();
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof String) {
            return Boolean.parseBoolean((String) result);
        }
        if (result instanceof Number) {
            return 0 != ((Number) result).intValue();
        }
        throw new IllegalArgumentException("Not parse parsable boolean value " + result);
    }

    public static Collection<String> toStringCollection(final Collection<Object> collection) {
        return Objects.requireNonNull(collection).stream().map(object -> {
            if (object instanceof String) {
                return (String) object;
            }
            if (object instanceof Task) {
                return ((Task) object).getName();
            }
            throw new IllegalArgumentException("The '" + object + "' instance conversion is not supported");
        }).collect(Collectors.toList());
    }
}
