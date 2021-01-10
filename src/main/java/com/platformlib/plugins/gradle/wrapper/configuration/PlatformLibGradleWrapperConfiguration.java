package com.platformlib.plugins.gradle.wrapper.configuration;

import com.platformlib.plugins.gradle.wrapper.utility.PlatformLibGradleWrapperUtility;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PlatformLibGradleWrapperConfiguration {
    private Supplier<Boolean> activateBy = () -> false;
    private Map<String, String> projectProperties = new HashMap<>();
    private Map<String, String> systemPropertiesArgs = new HashMap<>();
    private Collection<String> excludedProjectProperties = new ArrayList<>();
    private Collection<String> excludedSystemPropertiesArgs = new ArrayList<>();
    private String javaHome;
    private boolean parallelExecution;
    private boolean dryRun;

    public PlatformLibGradleWrapperConfiguration() {
    }

    public PlatformLibGradleWrapperConfiguration(final PlatformLibGradleWrapperConfiguration platformLibGradleWrapperConfiguration) {
        configure(platformLibGradleWrapperConfiguration);
    }

    public void configure(final PlatformLibGradleWrapperConfiguration platformLibGradleWrapperConfiguration) {
        this.activateBy = platformLibGradleWrapperConfiguration.getActivateBy();
        this.projectProperties.putAll(platformLibGradleWrapperConfiguration.projectProperties);
        this.systemPropertiesArgs.putAll(platformLibGradleWrapperConfiguration.systemPropertiesArgs);
        this.excludedProjectProperties.addAll(platformLibGradleWrapperConfiguration.excludedProjectProperties);
        this.excludedSystemPropertiesArgs.addAll(platformLibGradleWrapperConfiguration.excludedSystemPropertiesArgs);
        this.javaHome = platformLibGradleWrapperConfiguration.getJavaHome();
        this.parallelExecution = platformLibGradleWrapperConfiguration.parallelExecution;
        this.dryRun = platformLibGradleWrapperConfiguration.dryRun;
    }

    public Supplier<Boolean> getActivateBy() {
        return activateBy;
    }

    public void setActivateBy(final Supplier<Boolean> activateBy) {
        this.activateBy = activateBy;
    }

    public void activateBy(final Closure<?> activateByClosure) {
        this.activateBy = () -> PlatformLibGradleWrapperUtility.callBooleanClosure(activateByClosure);
    }

    public Map<String, String> getProjectProperties() {
        return projectProperties;
    }

    public void setProjectProperties(Map<String, String> projectProperties) {
        this.projectProperties = projectProperties;
    }

    public Map<String, String> getSystemPropertiesArgs() {
        return systemPropertiesArgs;
    }

    public void setSystemPropertiesArgs(Map<String, String> systemPropertiesArgs) {
        this.systemPropertiesArgs = systemPropertiesArgs;
    }

    public Collection<String> getExcludedProjectProperties() {
        return excludedProjectProperties;
    }

    public void setExcludedProjectProperties(Collection<String> excludedProjectProperties) {
        this.excludedProjectProperties = excludedProjectProperties;
    }

    public Collection<String> getExcludedSystemPropertiesArgs() {
        return excludedSystemPropertiesArgs;
    }

    public void setExcludedSystemPropertiesArgs(Collection<String> excludedSystemPropertiesArgs) {
        this.excludedSystemPropertiesArgs = excludedSystemPropertiesArgs;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public boolean isParallelExecution() {
        return parallelExecution;
    }

    public void setParallelExecution(boolean parallelExecution) {
        this.parallelExecution = parallelExecution;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
