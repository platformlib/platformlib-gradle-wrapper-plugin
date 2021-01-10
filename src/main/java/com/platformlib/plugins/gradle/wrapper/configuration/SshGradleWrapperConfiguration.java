package com.platformlib.plugins.gradle.wrapper.configuration;

public class SshGradleWrapperConfiguration extends PlatformLibGradleWrapperConfiguration {
    private final String name;
    private String host;

    public SshGradleWrapperConfiguration(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
