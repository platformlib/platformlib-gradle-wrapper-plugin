package com.platformlib.plugins.gradle.wrapper

import com.platformlib.process.api.ProcessInstance
import com.platformlib.process.local.factory.LocalProcessBuilderFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.toPath

val logger = LoggerFactory.getLogger(GradleDsl::class.java)!!
const val platformlibGradleWrapperVersionPropertyName = "platformlib-gradle-wrapper.version"
val gradleWrapperPluginVersion: String = System.getProperty(platformlibGradleWrapperVersionPropertyName, System.getenv().getOrDefault(platformlibGradleWrapperVersionPropertyName, "1.0-SNAPSHOT"))

class GradleDsl {
}

data class GradleExec(val gradleProjectDir: Path,
                      val gradleProcessInstance: ProcessInstance)

fun gradle(gradleProject: String): GradleExec {
    return gradle(gradleProject) { }
}

fun gradle(gradleProject: String, init: GradleDsl.() -> Unit): GradleExec {
    val gradleDsl = GradleDsl()
    gradleDsl.init()
    val projectPath = Objects.requireNonNull(GradleDsl::class.java.getResource("/gradle-projects/$gradleProject"), "The project $gradleProject hasn't been found in resources")!!.toURI().toPath()
    val gradleProcessInstance = LocalProcessBuilderFactory
            .newLocalProcessBuilder()
            .logger { it.logger(logger) }
            .processInstance { it.unlimited() }
            .defaultExtensionMapping()
            .workDirectory(projectPath)
            .command((if (File.pathSeparatorChar == ':') "./" else "") +   "gradlew")
            .build().execute("clean", "build", "-P$platformlibGradleWrapperVersionPropertyName=$gradleWrapperPluginVersion")
            .toCompletableFuture()
            .join().also {
                if (it.exitCode != 0) {
                    throw IllegalStateException("Fail to run gradle command because of exit code $${it.exitCode}")
                }
            }
    return GradleExec(projectPath, gradleProcessInstance)
}
