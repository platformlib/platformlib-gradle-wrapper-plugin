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
    var tasks: Collection<String> = emptyList()
    var expectedFailure = false
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
    val cla = ArrayList<String>()
    if (gradleDsl.tasks.isEmpty()) {
        cla.addAll(listOf("clean", "build"))
    } else {
        cla.addAll(gradleDsl.tasks)
    }
    cla.add("-P$platformlibGradleWrapperVersionPropertyName=$gradleWrapperPluginVersion")
    val gradleProcessInstance = LocalProcessBuilderFactory
            .newLocalProcessBuilder()
            .logger { it.logger(logger) }
            .processInstance { it.unlimited() }
            .defaultExtensionMapping()
            .workDirectory(projectPath)
            .command((if (File.pathSeparatorChar == ':') "./" else "") +   "gradlew")
            .build().execute(*cla.toArray())
            .toCompletableFuture()
            .join().also {
                if (!gradleDsl.expectedFailure && it.exitCode != 0) {
                    throw IllegalStateException("Fail to run gradle command because of exit code ${it.exitCode}")
                }
                if (gradleDsl.expectedFailure && it.exitCode == 0) {
                    throw IllegalStateException("The gradle has been executed successfully, but waited fail")
                }
            }
    return GradleExec(projectPath, gradleProcessInstance)
}
