package com.platformlib.plugins.gradle.wrapper.extension;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public interface PlatformLibDockerWrapperExtension {
    Property<String> getImage();
    Property<String> getWorkDir();
    ListProperty<String> getDockerOptions();
    ListProperty<String> getBindMounts();
    MapProperty<String, String> getEnv();
    ListProperty<String> getCommandAndArguments();
}
