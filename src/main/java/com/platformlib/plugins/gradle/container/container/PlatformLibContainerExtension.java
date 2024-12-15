package com.platformlib.plugins.gradle.container.container;

import java.util.ArrayList;
import java.util.List;

public class PlatformLibContainerExtension {
    public static final String EXTENSION_NAME = "platformLibContainer";
    private List<String> commandAndArguments = new ArrayList<>();

    public List<String> getCommandAndArguments() {
        return commandAndArguments;
    }

    public void setCommandAndArguments(List<String> commandAndArguments) {
        this.commandAndArguments = commandAndArguments;
    }
}
