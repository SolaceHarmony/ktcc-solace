import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.approximateDeclarationType
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version "2.0.20-RC2" apply true
    id("org.barfuin.gradle.taskinfo") version "2.2.0"
    idea
}

//project.gradle.taskGraph.whenReady { println(project.gradle.taskGraph.allTasks) }

repositories {
    mavenLocal()
    mavenCentral()
}

//val jsIndex = file("src/jsMain/resources/index.html")
//jsIndex.textContent = jsIndex.textContent.replace(Regex("Kotlin C Compiler WIP Version ([\\d\\.]*)"), "Kotlin C Compiler WIP Version $version")

//println(file("src/commonMain/kotlin/com/soywiz/ktcc/internal/version.kt").textContent)

//fun KotlinTargetContainerWithPresetFunctions.common(callback: KotlinOnlyTarget<*>.() -> Unit) {
//    callback(presets.getByName("common").createTarget("common") as KotlinOnlyTarget<*>)
//}

//val enableNative = false
val enableNative = System.getenv("KTCC_NO_ENABLE_NATIVE").isNullOrBlank()

kotlin {
    fun KotlinTarget.configureAll() {
        compilations.all {
            //kotlinOptions.freeCompilerArgs = listOf("-progressive", "-Xskip-metadata-version-check")
        }
    }

    fun KotlinNativeTarget.configureNative() {
        configureAll()
        binaries {
            executable()
        }
    }



    //common {
    //}

    jvm {
        configureAll()
    }
    js {
        configureAll()
        browser()
        binaries.executable()
        compilerOptions {
            this.target = "es2015"
            this.useEsClasses = true
            sourceMap = true
            moduleKind = JsModuleKind.MODULE_ES
        }
    }

    if (enableNative) {
        macosArm64 { configureNative() }
        macosX64 { configureNative() }
        linuxX64 { configureNative() }
        mingwX64 { configureNative() }

        //sourceSets {
        //    val nativeMain = this.create("nativeMain")
        //    val nativeTest = this.create("nativeTest")
        //    configure(listOf(this.getByName("macosX64Main"), this.getByName("macosArm64Main"), this.getByName("linuxX64Main"), this.getByName("mingwX64Main"))) {
        //        dependsOn(nativeMain)
        //    }
        //}
    }

    sourceSets["commonMain"].kotlin.srcDir("build/gen")

    dependencies {
        //val serializationRuntimeVersion = "0.11.1"
        //commonMainImplementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
        //add("jsMainImplementation", "org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serializationRuntimeVersion")

        commonMainImplementation("org.jetbrains.kotlin:kotlin-stdlib-common")
        commonTestImplementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        commonTestImplementation("org.jetbrains.kotlin:kotlin-test-common")
        add("jsTestImplementation", "org.jetbrains.kotlin:kotlin-test-js")
        add("jvmTestImplementation", "org.jetbrains.kotlin:kotlin-test-junit")
        add("jvmTestImplementation", "junit:junit:4.12")
        add("jvmMainImplementation", "org.jetbrains.kotlin:kotlin-compiler")

        // Scripting
        //add("jvmMainImplementation", "org.jetbrains.kotlin:kotlin-script-runtime")
        //add("jvmMainImplementation", "org.jetbrains.kotlin:kotlin-scripting-compiler")
        //add("jvmMainImplementation", "org.jetbrains.kotlin:kotlin-scripting-jsr223")
    }
}

val mainClassName = "com.soywiz.ktcc.cli.CLI"

val jsCompilations = kotlin.targets["js"].compilations

open class GenerateSourcesTask : DefaultTask() {
    private val rootDir = project.rootDir
    private val version = project.version

    private val GENERATED_DO_NOT_MODIFY = "// GENERATED. Do not modify"

    private fun String.escapeTripleQuote(): String {
        return this.replace("$", "\${DOLLAR}").replace("\"\"\"", "\${TRIPLET}")
    }

    private operator fun File.get(key: String) = File(this, key)
    private var File.textContent get() = this.readText(); set(value) = run { this.writeText(value) }

    fun generateIncludes(): String {
        val lines = arrayListOf<String>()
        lines += GENERATED_DO_NOT_MODIFY
        lines += "package com.soywiz.ktcc.headers"
        lines += "private val DOLLAR = '$'"
        lines += "private val TRIPLET = \"\\\"\\\"\\\"\""
        lines += "val CStdIncludes = CIncludes().apply {"

        val includeDir = File(rootDir, "include").absoluteFile
        for (file in includeDir.walkTopDown().toList().sortedBy { it.absolutePath }) {
            if (!file.name.endsWith(".h")) continue
            val ktFile = File(file.absolutePath.removeSuffix(".h") + ".kt")
            val cFile = File(file.absolutePath.removeSuffix(".h") + ".c")
            //println(file)
            lines += buildString {
                append("FILE(\"${file.relativeTo(includeDir).path}\", \"\"\"${file.readText().escapeTripleQuote()}\"\"\"")
                if (ktFile.exists()) append(", ktImpl = \"\"\"${ktFile.readText().escapeTripleQuote()}\"\"\"")
                if (cFile.exists()) append(", cImpl = \"\"\"${cFile.readText().escapeTripleQuote()}\"\"\"")
                append(")")
            }
        }

        lines += "}.map.toMap()"
        lines += ""
        return lines.joinToString("\n")
    }

    @TaskAction
    fun action() {
        File(rootDir, "build/gen/kotlin/com/soywiz/ktcc/internal/version.kt").also { it.parentFile.mkdirs() }.textContent = "package com.soywiz.ktcc.internal\n\ninternal val KTCC_VERSION = \"$version\""
        File(rootDir, "build/gen/kotlin/com/soywiz/ktcc/headers/CStdIncludesGenerated.kt").also { it.parentFile.mkdirs() }.writeText(generateIncludes())
        File(rootDir, "build/gen/kotlin/com/soywiz/ktcc/gen/KotlinGen.kt").also { it.parentFile.mkdirs() }.writeText(
            "$GENERATED_DO_NOT_MODIFY\n" +
                    "package com.soywiz.ktcc.gen\n\n" +
                    "private val DOLLAR = '$'\n" +
                    "private val TRIPLET = \"\\\"\\\"\\\"\"\n" +
                    "val KotlinRuntime = \"\"\"${File(rootDir, "src/commonMain/kotlin/Runtime.kt").readText().escapeTripleQuote()}\"\"\"\n" +
                    "val KotlinRuntimeJvm = \"\"\"${File(rootDir, "src/jvmMain/kotlin/RuntimeJvm.kt").readText().escapeTripleQuote()}\"\"\"\n"
        )
    }
}

tasks {
    val generateSources by creating(GenerateSourcesTask::class)

    //val runDceJsKotlin = named<KotlinJsDce>("runDceJsKotlin").get()
    val jvmJar by getting(Jar::class)

    val compileKotlinJvm by getting(KotlinCompile::class)

    compileKotlinJvm.dependsOn(generateSources)
    //println("compileKotlinJvm=${compileKotlinJvm::class}")

    val fatJar by creating(Jar::class) {
        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
        archiveBaseName.set("${project.name}-all")
        //archiveVersion.set(null as String?)
        archiveVersion.set("")
        archiveAppendix.set("")

        manifest {
            attributes("Main-Class" to mainClassName)
        }

        //println(configurations.names.toList())
        from(provider { configurations["jvmRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) } })

        with(jvmJar)
    }

    //val jsWebResourcesDce by creating(Copy::class) {
    //    dependsOn(runDceJsKotlin)
    //    into(file("docs"))
    //    includeEmptyDirs = false
    //    from(kotlin.sourceSets["jsMain"].resources)
    //    from(kotlin.sourceSets["commonMain"].resources)
    //}

    val jsWebResources by creating(Copy::class) {
        dependsOn("jsMainClasses")
        into(file("docs"))
        includeEmptyDirs = false
        from(kotlin.sourceSets["jsMain"].resources)
        from(kotlin.sourceSets["commonMain"].resources)
    }

    //val jsWebDce by creating(Copy::class) {
    //    dependsOn(jsWebResourcesDce)
    //    into(file("docs"))
    //    includeEmptyDirs = false
    //    exclude("**/*.kjsm", "**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.MF", "**/*.meta.js", "**/*.map")
    //    from(named("compileProductionExecutableKotlinJs").get().outputs)
    //}

    val jsWeb by creating(Copy::class) {
        dependsOn(jsWebResources)
        into(file("docs"))
        includeEmptyDirs = false
        exclude("**/*.kjsm", "**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.MF", "**/*.meta.js", "**/*.map")
        //from(named("compileProductionExecutableKotlinJs").get().outputs)
        from(named("compileDevelopmentExecutableKotlinJs").get().outputs)
    }

    val buildDockerImage by creating {
        afterEvaluate {
            dependsOn("linkReleaseExecutableLinuxX64")
        }
        doLast {
            exec { commandLine = listOf("docker", "build", ".", "-t", "soywiz/ktcc:latest") }
        }
    }
    val buildDockerImageAndPublish by creating {
        dependsOn("buildDockerImage")
        doLast {
            exec { commandLine = listOf("docker", "push", "soywiz/ktcc:latest") }
        }
    }
    project.tasks.withType(ProcessResources::class) {
        this.dependsOn(generateSources)
        //println("resources=$this : ${this::class}")
    }

    val prepare by creating {
        dependsOn(generateSources)
    }
}

afterEvaluate {
    tasks.getByName("linuxX64Test").enabled = false
    tasks.getByName("macosArm64Test").enabled = false
    tasks.getByName("macosX64Test").enabled = false
    tasks.getByName("mingwX64Test").enabled = false
}

//compilations.all {
//    kotlinOptions {
//        freeCompilerArgs = ["-progressive", "-Xskip-metadata-version-check"]
//    }
//}

rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin::class.java) {
    //rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().download = false
}

tasks.withType(Test::class.java).all {
    testLogging {

        setEvents(setOf(TestLogEvent.PASSED,TestLogEvent.SKIPPED,TestLogEvent.FAILED,TestLogEvent.STANDARD_OUT,TestLogEvent.STANDARD_ERROR))
        //setEvents(setOf("skipped", "failed", "standardError"))
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

idea {
    module {
        excludeDirs = excludeDirs + setOf(file(".gradle"), file("@old"), file("doc"), file("docs"), file("samples"), file("gradle"), file("build"), file("include"), file(".idea"), file(".github"), file("temp"), file("build/gen"))
    }
}
