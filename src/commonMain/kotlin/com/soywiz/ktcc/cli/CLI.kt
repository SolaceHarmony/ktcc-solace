package com.soywiz.ktcc.cli

import com.soywiz.ktcc.*
import com.soywiz.ktcc.compiler.*
import com.soywiz.ktcc.eval.*
import com.soywiz.ktcc.gen.*
import com.soywiz.ktcc.internal.*
import com.soywiz.ktcc.util.*
import kotlin.jvm.*

object CLI {
    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val argsReader = ListReader(args.toList(), "")
        var execute = false
        var print = false
        var runtime: Boolean? = null
        val sourceFiles = arrayListOf<String>()
        val execArgs = arrayListOf<String>()
        val defines = arrayListOf<String>()
        val includeFolders = arrayListOf<String>()
        val libFolders = arrayListOf<String>()
        val libs = arrayListOf<String>()
        var debugLevel = 0
        var compileOnly: Boolean? = null
        var optimizeLevel = 0
        var outputFile: String? = null
        var preprocessOnly = false
        val warnings = arrayListOf<String>()
        val extra = arrayListOf<String>()
        var targetName = Targets.default.name
        var subTarget: String? = null
        var tempName: String? = null
        var visibility: String? = null

        fun showHelp() {
            println("ktcc [-e] [-p] ...files[.c][.o]")
            println("")
            println(" -p - Print")
            println(" -W* - GCC-compatible warnings (ignored)")
            println(" -e - Execute")
            println(" -c - Compile-only")
            println(" -o - Output file")
            println(" -version - Prints version")
            println(" -Ttarget - Selects the output target. One of [${Targets.all.joinToString(", ") { it.name } }]")
            println(" -g[0123] - Debug level")
            println(" -O[0123|fast|s] - Optimization level")
            println(" -E - Preprocess only")
            println(" --runtime - Include runtime")
            println(" --visibility=<public|internal> - Sets visibility for classes")
            println(" --no-runtime - No runtime")
            println(" --subtarget=<common|jvm> - Subtarget for code generation")
            println(" --tempname=<tempname> - Uses a temporal name for the temp folder when -e is enabled")
            println(" -Dname - Add define")
            println(" -Ipath - Add include folder")
            println(" -Lpath - Add lib folder")
            println(" -lname - Add lib")
        }

        while (!argsReader.eof) {
            val v = argsReader.read()
            when {
                v in setOf("-?", "/?", "-h", "-H") -> return showHelp()
                v == "-o" -> outputFile = argsReader.read()
                v == "-p" -> print = true
                v == "-e" -> execute = true
                v == "-E" -> preprocessOnly = true
                v == "-c" -> compileOnly = true
                v == "--runtime" -> runtime = true
                v == "--no-runtime" -> runtime = false
                v.startsWith("--subtarget=") -> subTarget = v.removePrefix("--subtarget=")
                v.startsWith("--tempname=") -> tempName = v.removePrefix("--tempname=").takeIf { it.isNotEmpty() }
                v.startsWith("--visibility=") -> visibility = v.removePrefix("--visibility=")
                v.startsWith("-O") -> optimizeLevel = when (v.substring(2)) {
                    "", "1" -> 1
                    "2" -> 2
                    "3" -> 3
                    "fast" -> 0
                    "s" -> 0
                    else -> error("Unknown optimization level $v")
                }
                v == "-version" -> {
                    println("KTCC ${KTCC.VERSION}")
                    return
                }
                v.startsWith("-T") -> targetName = v.substring(2)
                v.startsWith("-W") -> warnings += v.substring(2)
                v.startsWith("-f") -> extra += v.substring(2)
                v.startsWith("-g") -> debugLevel = v.substring(2).toIntOrNull() ?: 3
                v.startsWith("-D") -> defines += v.substring(2)
                v.startsWith("-I") -> includeFolders += v.substring(2)
                v.startsWith("-L") -> libFolders += v.substring(2)
                v.startsWith("-l") -> libs += v.substring(2)
                v.startsWith("-") -> {
                    println("Unrecognized switch '$v'")
                }
                else -> {
                    if (execute) {
                        execArgs += v
                    } else {
                        sourceFiles += v
                    }
                }
            }
        }

        if (sourceFiles.isEmpty()) {
            return showHelp()
        }

        //println("args=${args.toList()}, execute=$execute, sourceFiles=$sourceFiles")

        val finalCOutput = CCompiler.preprocess(sourceFiles, defines, includeFolders, optimizeLevel)
        val finalCSource = finalCOutput.code

        if (preprocessOnly) {
            println(finalCSource)
        } else if (compileOnly == true) {
            if (outputFile != null) {
                writeFile(outputFile, finalCSource.encodeToByteArray())
            } else {
                println(finalCSource)
            }
        } else {
            val ckEval = CKotlinEvaluator(Targets[targetName])
            val finalKtSource = ckEval.generateKotlinCodeWithRuntime(
                finalCSource,
                finalCOutput.info.copy(
                    runtime = runtime ?: execute,
                    subTarget = subTarget ?: (if (execute) "jvm" else "common"),
                    visibility = visibility ?: finalCOutput.info.visibility ?: "public"
                )
            )

            if (!execute || print) {
                if (outputFile != null) {
                    writeFile(outputFile, finalKtSource.encodeToByteArray())
                } else {
                    println(finalKtSource)
                }
            }

            if (execute) {
                val result = ckEval.evaluateKotlinRaw(finalKtSource, execArgs.toTypedArray(), tempName = tempName)
                if (result is Int) {
                    //System.exit(result)
                    if (result != 0) {
                        throw Exception("Program exited with $result code")
                    }
                }
            }
        }
    }
}
