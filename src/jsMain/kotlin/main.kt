import com.soywiz.ktcc.*
import com.soywiz.ktcc.compiler.*
import com.soywiz.ktcc.gen.*
import com.soywiz.ktcc.js.*
import com.soywiz.ktcc.parser.*
import com.soywiz.ktcc.tokenizer.*
import com.soywiz.ktcc.types.*
import kotlinx.browser.*
import org.w3c.dom.*
import kotlin.browser.*

val completionNode by lazy { document.getElementById("completion").unsafeCast<HTMLInputElement>() }
val debugNode by lazy { document.getElementById("debug").unsafeCast<HTMLInputElement>() }
val autocompileNode by lazy { document.getElementById("autocompile").unsafeCast<HTMLInputElement>() }
val targetNode by lazy { document.getElementById("target").unsafeCast<HTMLSelectElement>() }
val includeRuntimeNode by lazy { document.getElementById("include-runtime").unsafeCast<HTMLInputElement>() }

val debug get() = debugNode.checked

fun main(args: Array<String>) {
    //println("// args=${args.toList()}")
    //CLI.main(args)
    println("Waiting for DOMContentLoaded...")
    document.addEventListener("DOMContentLoaded", { e ->
        println("READY")

        document.getElementById("version")?.textContent = KTCC.VERSION

        //val sourcesNode = (document.getElementById("sources") as? HTMLTextAreaElement)
        //val transpiledNode = (document.getElementById("transpiled") as? HTMLTextAreaElement)

        //val sourcesEditor = ace.edit("sources", jsObject("maxLines" to 30, "minLines" to 2)).apply {
        val sourcesEditor = ace.edit("sources").apply {
            setTheme("ace/theme/monokai")
            setOptions(jsObject(
                    "enableBasicAutocompletion" to true,
                    "enableLiveAutocompletion" to true
            ))
            session.setMode("ace/mode/c_cpp")
        }

        sourcesEditor.asDynamic().keyBinding.addKeyboardHandler { data: KeyData, hash: Int, keyString: String, keyCode: Int, event: dynamic ->
            if (hash == -1 && (keyString == "." || keyString == ">")) {
                //println("should startAutocomplete")
                window.setTimeout({
                    data.editor.execCommand("startAutocomplete")
                }, 50)
            }
            val cur = data.editor.getCursorPosition()
            window.localStorage["row0"] = cur.row0.toString()
            window.localStorage["column"] = cur.column.toString()
            Unit
            //console.log(data, hash, keyString, keyCode, event)
        }

        //sourcesEditor.asDynamic().commands.addCommand(jsObject(
        //    "name" to "dotPress",
        //    "bindKey" to jsObject("win" to ".", "mac" to "@"),
        //    "exec" to { editor: Editor ->
        //        println("Typed .")
        //        editor.execCommand("startAutocomplete")
        //    }
        //))

        //val transpiledEditor = ace.edit("transpiled", jsObject("maxLines" to 30, "minLines" to 2)).apply {
        val preprocessorEditor = ace.edit("preprocessor").apply {
            setTheme("ace/theme/monokai")
            setOptions(jsObject())
            session.setMode("ace/mode/c_cpp")
            setReadOnly(true)
        }

        val transpiledEditor = ace.edit("transpiled").apply {
            setTheme("ace/theme/monokai")
            setOptions(jsObject())
            setReadOnly(true)
        }

        fun getCurrentTarget(): BaseTarget {
            return Targets.byName[targetNode.value] ?: Targets.default
        }

        fun updateTranspiledMode(target: BaseTarget = getCurrentTarget()) {
            transpiledEditor.session.setMode(when (target) {
                KotlinTarget -> "ace/mode/kotlin"
                CTarget -> "ace/mode/c_cpp"
                else -> "ace/mode/c_cpp"
            })
        }

        updateTranspiledMode()

        window.asDynamic().sourcesEditor = sourcesEditor
        window.asDynamic().preprocessorEditor = preprocessorEditor
        window.asDynamic().transpiledEditor = transpiledEditor

        val cref = object : CompilationRef() {
            override fun updated() {
                val curpos = sourcesEditor.getCursorPosition()
                val comp = ccompilation
                // @TODO: synchronize views
                if (comp != null) {
                    val opos = ProgramParser.PosWithFile(curpos.row1, 0, "main.c")
                    val translate = comp.parser.translatePos(opos)
                    if (translate != null) {
                        //println("opos=$opos, translate=$translate")
                        preprocessorEditor.gotoLine(translate.row1)
                        preprocessorEditor.selection.selectDown()
                        preprocessorEditor.scrollToLine(translate.row1, true, true)
                    }
                }
            }
        }

        sourcesEditor.selection.onChangeCursor { event, selection ->
            cref.updated()
            //println("changeCursor: row0=${sourcesEditor.getCursorPosition().row0}")
        }

        fun compile() {
            val target = getCurrentTarget()
            val sources = sourcesEditor.getValue()
            updateTranspiledMode(target)

            when (target) {
                KotlinTarget -> {
                    transpiledEditor.session.setMode("ace/mode/kotlin")
                }
            }

            window.localStorage["ktccProgram"] = sources

            files.clear()

            //println("sources=$sources")
            files["main.c"] = utf8Encode(sources)

            try {
                val cfileOut = CCompiler.preprocess(listOf("main.c"))
                val cfile = cfileOut.code
                preprocessorEditor.setValue(cfile, -1)
                try {
                    val compilation = CCompiler.compile(cfile, cfileOut.info, includeRuntime = includeRuntimeNode.checked, target = target)
                    cref.setCompilation(compilation)
                    val ktfile = compilation.source
                    val warnings = compilation.warnings.map { "// WARNING: $it" }.joinToString("\n")
                    val errors = compilation.errors.map { "// ERROR: $it" }.joinToString("\n")

                    if (compilation.warnings.isNotEmpty() || compilation.errors.isNotEmpty()) {
                        fun ProgramMessage.toAceAnnotation(type: String) = AceAnnotation(message, row0, column = columnStart, type = type)

                        val warningAnnotations = compilation.warnings.map { it.toAceAnnotation("warning") }
                        val errorAnnotations = compilation.errors.map { it.toAceAnnotation("error") }

                        sourcesEditor.session.setAnnotations(
                                (errorAnnotations + warningAnnotations).toTypedArray()
                        )
                    } else {
                        sourcesEditor.session.clearAnnotations()
                    }

                    transpiledEditor.setValue("$warnings\n$errors\n$ktfile".trim(), -1)
                } catch (e: Throwable) {
                    transpiledEditor.setValue("${e.asDynamic().stack ?: e}", -1)
                }
            } catch (e: Throwable) {
                preprocessorEditor.setValue("${e.asDynamic().stack ?: e}", -1)
                transpiledEditor.setValue("", -1)
            }

            cref.updated()
        }


        targetNode.onchange = { e ->
            //
            //cref.updated()
            localStorage.setItem("ktccTarget", targetNode.value)
            compile()
        }
        targetNode.innerHTML = ""
        for (target in Targets.all) {
            targetNode.appendChild(document.createElement("option").also {
                it.setAttribute("value", target.name)
                //it.setAttribute("selected", (target == Targets.default).toString())
                it.textContent = target.name
            })
        }

        val selectedTarget = Targets.byName[localStorage.getItem("ktccTarget")] ?: Targets.default
        targetNode.selectedIndex = Targets.all.indexOf(selectedTarget)

        var timeout = 0

        includeRuntimeNode.addEventListener("change", {
            compile()
        })

        sourcesEditor.on("change") { e ->
            window.clearTimeout(timeout)
            timeout = window.setTimeout({
                if (autocompileNode.checked) {
                    compile()
                }
            }, 500)
        }

        document.getElementById("compile")?.addEventListener("click", { e ->
            println("CLICKED!")
            compile()
        })

        val langTools = ace.require("ace/ext/language_tools")
        langTools.setCompleters(arrayOf(CCompletion(cref)))
        //langTools.addCompleter()

        val row0 = window.localStorage["row0"]?.toIntOrNull() ?: 0
        val column = window.localStorage["column"]?.toIntOrNull() ?: 0

        sourcesEditor.setValue(window.localStorage["ktccProgram"] ?: """
            #pragma ktcc module MyModule
            #pragma ktcc package com.soywiz.test

            #include <stdio.h>

            typedef struct {
                int a;
                union {
                    float f;
                    long int l;
                } u;
            } A;

            int main() {
                A a = {1};
                return 0;
            }
        """.trimIndent(), -1)

        sourcesEditor.focus()

        //sourcesEditor.selection.moveTo(row, column)
        window.setTimeout({
            sourcesEditor.gotoLine(row0 + 1, column, false)
            sourcesEditor.scrollToLine(row0 + 1, true)
        }, 0)

        window.localStorage[""]

        compile()
    })
}

open class CompilationRef {
    var ccompilation: CCompiler.Compilation? = null
        private set

    open fun updated() {
    }

    fun setCompilation(c: CCompiler.Compilation) {
        this.ccompilation = c
        updated()
    }
}

class CCompletion(val cref: CompilationRef) : AceCompleter {
    override fun getCompletions(editor: Editor, session: EditSession, pos: Pos, prefix: String, callback: (unk: Any?, completions: Array<AceCompletion>) -> Unit) {
        if (!completionNode.checked) return

        try {
            files["main.c"] = utf8Encode(editor.getValue())
            val cfileDef = CCompiler.preprocess(listOf("main.c"))
            val cfile = cfileDef.code
            val compilation = CCompiler.compile(cfile, cfileDef.info, includeRuntime = false)
            cref.setCompilation(compilation)
            val parser = compilation.parser
            val originalPos = ProgramParser.PosWithFile(pos.row1, pos.column, "main.c")
            val translatedPos = parser.translatePos(originalPos) ?: ProgramParser.Pos(1, 0)
            val foundToken = compilation.parser.findNearToken(translatedPos.row1, translatedPos.column0)
            //println("translatedPos=$translatedPos, pos=(${pos.row}, ${pos.column})")
            //println("token=$foundToken")
            val foundNodeTree = foundToken?.let { compilation.parser.findNodeTreeAtToken(compilation.program, it) } ?: listOf()
            val lastFoundNode = foundNodeTree.lastOrNull()
            //val expr = foundNodeTree.filterIsInstance<FieldAccessExpr>().lastOrNull()?.expr ?: foundNodeTree.filterIsInstance<Expr>().lastOrNull()
            val expr = foundNodeTree.filterIsInstance<FieldAccessExpr>().lastOrNull()?.left
            //println("foundNodeTree=$foundNodeTree")

            // DO NOT AUTOCOMPLETE INSIDE STRINGS
            if (foundNodeTree.any { it is StringConstant }) return

            //val extraInfos = if (lastFoundNode is Stm) {
            val typesInfos = if (true) {
                val res = parser.typedefAliases.values.map { TypeInfo(it) }
                if (debug) {
                    println("type infos: $res")
                }
                res
            } else {
                listOf()
            }

            val keywordsInfo = if (foundNodeTree.any { it is Stm }) {
                listOf("if", "else", "switch", "while", "do", "for").map { KeywordInfo(it) }
            } else {
                listOf()
            }

            if (debug) {
                println("expr=$expr, originalPos=$originalPos, translatedPos=$translatedPos")

                if (expr == null) {
                    for (node in foundNodeTree) {
                        println("  -> $node")
                    }
                }
            }

            val symbolInfos: List<AutocompletionInfo> = if (expr != null) {
                val exprType = expr.type
                val resolvedExprType2 = parser.resolve(exprType)
                val resolvedExprType = if (resolvedExprType2 is BasePointerType) resolvedExprType2.elementType else resolvedExprType2
                //println("resolvedExprType2: $resolvedExprType2")
                //println("resolvedExprType: $resolvedExprType")
                if (resolvedExprType is StructType) {
                    val structTypeInfo = resolvedExprType.getStructTypeInfo(compilation.parser)
                    //println("structTypeInfo : $structTypeInfo ")
                    structTypeInfo.fields.map { SymbolInfo(SymbolScope(null), it.name, it.type, it.node, CToken("")) }
                } else {
                    listOf()
                }
            } else {
                val scope = compilation.parser.getInnerSymbolsScopeAt(foundToken)
                val allSymbolNames = scope.getAllSymbolNames()
                val filteredSymbolNames = allSymbolNames.filter { it.contains(prefix, ignoreCase = true) }
                val symbolNames = if (filteredSymbolNames.isNotEmpty()) filteredSymbolNames else allSymbolNames
                symbolNames.map { scope[it] }.filterNotNull().filter { it.token.pos < 0 || (foundToken?.pos ?: 0) >= it.token.pos }
            }

            //println("token=$foundToken, node=$foundNodeTree")
            //println("token=$foundToken, scope=$scope, symbolNames=$symbolNames, symbolInfos=$symbolInfos")
            //println(scope)

            val combinedInfos = symbolInfos + typesInfos + keywordsInfo
            callback(null, combinedInfos.map {
                val typeStr = try {
                    //compilation.parser.resolve(it.type).toString()
                    it.desc
                } catch (e: Throwable) {
                    e.message ?: "Error Unknown"
                }
                val scoreMult = when {
                    it.name.startsWith(prefix) -> 20
                    it.name.startsWith(prefix, ignoreCase = true) -> 10
                    else -> 1
                }
                AceCompletion(it.name, it.name, typeStr, it.score * scoreMult)
            }.toTypedArray())
        } catch (e: Throwable) {
            console.log(e)
        }
    }

}
