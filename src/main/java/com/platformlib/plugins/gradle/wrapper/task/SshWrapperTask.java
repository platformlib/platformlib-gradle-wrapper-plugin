package com.platformlib.plugins.gradle.wrapper.task;

import com.platformlib.plugins.gradle.wrapper.configuration.SshGradleWrapperConfiguration;

import java.util.Collection;

public class SshWrapperTask extends AbstractWrapperTask<SshGradleWrapperConfiguration> {

    @Override
    protected void executeWrappedGradle(final Collection<String> gradleCommandAndArguments) {
    }
}
