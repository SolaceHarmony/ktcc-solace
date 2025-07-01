package com.soywiz.ktcc.parser

import com.soywiz.ktcc.preprocessor.*
import com.soywiz.ktcc.serializable.*
import com.soywiz.ktcc.tokenizer.*
import com.soywiz.ktcc.types.*
import com.soywiz.ktcc.util.*
import kotlin.math.*

abstract class AutocompletionInfo() {
    abstract val name: String
    abstract val desc: String
    open val score: Int = 0
}

data class KeywordInfo(val keyword: String) : AutocompletionInfo() {
    override val name: String = keyword
    override val desc: String = ""
}

data class TypeInfo(val type: Type) : AutocompletionInfo() {
    override val name: String = type.toString().removePrefix("struct ")
    override val desc: String = ""
}

data class SymbolInfo(val scope: SymbolScope, override val name: String, val type: Type, val node: Node, val token: CToken, val isIntrinsic: Boolean = false) : AutocompletionInfo() {
    override val desc get() = type.toString()
    override val score: Int get() = scope.level
}

class SymbolScope(val parent: SymbolScope?, var start: Int = -1, var end: Int = -1) {
    val level: Int = if (parent != null) parent.level + 1 else 0
    val isGlobal get() = parent == null

    val children = arrayListOf<SymbolScope>()
    val symbols = LinkedHashMap<String, SymbolInfo>()

    fun createInfo(name: String, type: Type, node: Node, token: CToken) = SymbolInfo(this, name, type, node, token)

    init {
        parent?.children?.add(this)
    }

    fun registerInfo(name: String, type: Type, node: Node, token: CToken) = register(createInfo(name, type, node, token))

    fun register(symbol: SymbolInfo) {
        symbols[symbol.name] = symbol
    }

    operator fun get(symbol: String): SymbolInfo? {
        return getHere(symbol) ?: parent?.get(symbol)
    }

    fun getHere(symbol: String): SymbolInfo? {
        return this.symbols[symbol]
    }

    fun getAllSymbolNames(out: MutableSet<String> = mutableSetOf()): Set<String> {
        out += symbols.keys
        parent?.getAllSymbolNames(out)
        return out
    }

    override fun toString(): String =
        "SymbolScope(level=$level, symbols=${symbols.keys}, children=${children.size}, parent=${parent != null}, start=$start, end=$end)"
}

data class ProgramMessage(val message: String, val token: CToken, val pos: Int, val marker: ProgramParser.Marker, val level: Level) {
    enum class Level { WARNING, ERROR }

    val file get() = marker.translatedFile
    val row1 get() = token.row - marker.rowDiff
    val row0 get() = row1 - 1
    val columnStart get() = token.columnStart
}

class ParserException(val info: ProgramMessage, val parent: Throwable?) : ExpectException(info.message) {
}

interface ProgramParserRef {
    val parser: ProgramParser
}

@Serializable
class FunctionScope {
    var name: String = ""
    var type: FunctionType? = null
    var hasGoto: Boolean = false
    val rettype: Type get() = type?.retType ?: Type.UNRESOLVED
}

val POINTER_SIZE = 4

data class Intrinsic(
    val name: String,
    val type: Type
) {
    companion object {
        val DUMMY_SYMBOL_SCOPE = SymbolScope(null)
    }
    fun toSymbol(): SymbolInfo? {
        return SymbolInfo(DUMMY_SYMBOL_SCOPE, name, type, DummyNode(dummy = true), CToken(name), isIntrinsic = true)
    }
}

class ProgramParser(items: List<String>, val tokens: List<CToken>, pos: Int = 0) : ListReader<String>(items, "<eof>", pos), ProgramParserRef,
    TypeResolver by ResolveCache() {
    init {
        for (n in 0 until items.size) tokens[n].tokenIndex = n
    }

    override val parser = this
    val typedefTypes = LinkedHashMap<String, ListTypeSpecifier>()
    val typedefAliases = LinkedHashMap<String, Type>()
    val current get() = this.peek()
    val strings = LinkedHashSet<String>()

    var dummyFuncCounter = 0
    fun generateDummyFuncName(): String = "dummy_func_${dummyFuncCounter++}"

    var structId = 0
    val structTypesByName = LinkedHashMap<String, StructTypeInfo>()
    val structTypesBySpecifier = LinkedHashMap<StructUnionTypeSpecifier, StructTypeInfo>()

    val intrinsics = listOf(
        "__kotlin__global__non__jvm__",
        "__kotlin__global__jvm__",
        "__kotlin__global__",
        "__kotlin__annotation__",
        "__kotlin__",
    ).map {
        Intrinsic(it, FunctionType(it, Type.VOID, listOf(FParam("str", Type.CHAR_PTR))))
    }.associateBy { it.name }
    var symbols = SymbolScope(null, 0, tokens.size)

    var _functionScope: FunctionScope? = null
    val functionScope: FunctionScope get() = _functionScope ?: error("Not inside a function")

    val warnings = arrayListOf<ProgramMessage>()
    val errors = arrayListOf<ProgramMessage>()

    fun token(pos: Int) = tokens.getOrElse(pos) { CToken("") }
    fun token(node: Node) = token(node.pos)

    fun reportWarning(msg: String, pos: Int = this.pos) {
        warnings += ProgramMessage(msg, token(pos), pos, currentMarker, ProgramMessage.Level.WARNING)
    }

    fun reportError(msg: String, pos: Int = this.pos) {
        errors += ProgramMessage(msg, token(pos), pos, currentMarker, ProgramMessage.Level.ERROR)
    }

    fun reportError(exception: ParserException) {
        errors += exception.info
    }

    fun reportError(exception: Throwable) {
        when (exception) {
            is ParserException -> errors += exception.info
            else -> reportError(exception.message ?: "error")
        }
    }

    fun parserException(message: String, pos: Int = this.pos, parent: Throwable? = null): Nothing =
        throw ParserException(ProgramMessage(message, token(pos), pos, currentMarker, ProgramMessage.Level.ERROR), parent)

    override fun createExpectException(message: String): ExpectException = parserException(message)

    inline fun <T> scopeFunction(crossinline callback: () -> T): T {
        val old = _functionScope
        _functionScope = FunctionScope()
        try {
            return callback()
        } finally {
            _functionScope = old
        }
    }

    inline fun <T> scopeSymbols(crossinline callback: () -> T): T {
        val old = symbols
        return try {
            symbols = SymbolScope(old, pos, pos)
            callback()
        } finally {
            symbols.end = pos
            symbols = old
        }
    }

    fun getStructTypeInfo(name: String): StructTypeInfo = structTypesByName[name] ?: error("Can't find type by name $name")

    fun getStructTypeInfo(spec: StructUnionTypeSpecifier): StructTypeInfo =
        structTypesBySpecifier[spec] ?: run {
            // Create a placeholder struct type for undefined structs
            val structName = spec.id?.name ?: "Anonymous${structId++}"
            val structInfo = StructTypeInfo(structName, spec, StructType(spec), spec)
            structTypesBySpecifier[spec] = structInfo
            structTypesByName[structName] = structInfo
            structInfo
        }

    fun findNearToken(row: Int, column: Int): CToken? {
        val testIndex = genericBinarySearch(0, size, { from, to, low, high -> low }) {
            val token = tokens[it]
            val comp1 = token.row.compareTo(row)
            if (comp1 == 0) token.columnMiddle.compareTo(column) else comp1
        }
        return tokens.getOrNull(testIndex)
    }

    fun SymbolScope.contains(token: CToken): Boolean = token.tokenIndex in this.start..this.end

    fun getInnerSymbolsScopeAt(token: CToken?, scope: SymbolScope = this.symbols): SymbolScope {
        if (token != null) {
            for (childScope in scope.children) {
                //println("token=$token, contains=${childScope.contains(token)}, childScope=$childScope")
                if (childScope.contains(token)) return getInnerSymbolsScopeAt(token, childScope)
            }
        }
        return scope
    }

    fun findNodeTreeAtIndex(root: Node, pos: Int, out: ArrayList<Node> = arrayListOf()): List<Node> {
        //println("findNodeAtIndex: $pos : ${root.pos}-${root.endPos} : $root")
        out.add(root)
        root.visitChildren {
            if (pos in it.pos..it.endPos) {
                return findNodeTreeAtIndex(it, pos, out)
            }
        }
        return out
    }

    fun findNodeTreeAtToken(root: Node, foundToken: CToken, out: ArrayList<Node> = arrayListOf()): List<Node> {
        return findNodeTreeAtIndex(root, foundToken.tokenIndex, out)
    }

    data class Marker(
        val originalPos: Int = 0,
        val originalRow1: Int = 0,
        val translatedFile: String = "",
        val translatedRow1: Int = 0
    ) {
        val rowDiff = (originalRow1 - translatedRow1)
    }

    val markers = arrayListOf<Marker>()
    var currentMarker = Marker()

    fun consumeLineMarkers() {
        while (true) {
            val peek = peekOutside()

            if (peek == "#") {
                val markerPos = pos
                expect("#")
                val row = read()
                val fileQuoted = read()

                if (!fileQuoted.startsWith('"')) {
                    error("Invalid '#' $row fileQuoted='$fileQuoted'")
                }

                currentMarker = Marker(
                    originalPos = markerPos,
                    originalRow1 = token(markerPos).row + 1,
                    translatedRow1 = row.toInt(),
                    translatedFile = fileQuoted.cunquoted
                )
                markers += currentMarker
                continue
            }
            if (peek.startsWith("//")) {
                skip()
                println("COMMENT! $peek")
                continue
            }
            if (peek.startsWith("/*")) {
                skip()
                println("COMMENT! $peek")
                continue
            }
            break
        }
    }

    data class Pos(val row1: Int, val column0: Int) {
        val row0 get() = row1 - 1
    }

    data class PosWithFile(val row1: Int, val column0: Int, val file: String) {
        val row0 get() = row1 - 1
    }

    // From preprocessor to original file
    fun translatePos(pos: Pos): PosWithFile {
        TODO("translatePos")
    }

    // From original file to preprocessor
    fun translatePos(pos: PosWithFile): Pos? {
        for (marker in markers.reversed()) {
            if (marker.translatedFile == pos.file && pos.row1 >= marker.translatedRow1) {
                return Pos(pos.row1 + marker.rowDiff, pos.column0)
            }
        }
        return null
    }

    override fun toString(): String = "ProgramParser(current='${peekOutside()}', pos=$pos, token=${tokens.getOrNull(pos)}, marker=$currentMarker)"
}

fun Node.visitAllDescendants(callback: (Node) -> Unit) {
    this.visitChildren {
        callback(it)
        it.visitAllDescendants(callback)
    }
}

inline fun Node.visitChildren(callback: (Node) -> Unit) {
    val visitor = ArrayChildrenVisitor()
    this.visitChildren(visitor)
    for (node in visitor.out) callback(node)
}

interface ChildrenVisitor {
    operator fun invoke(a: Node)
}

//typealias ChildrenVisitor = ((Node) -> Unit)

class FuncChildrenVisitor(val func: (Node) -> Unit) : ChildrenVisitor {
    override fun invoke(a: Node) = func(a)
}

class ArrayChildrenVisitor(val out: ArrayList<Node> = arrayListOf()) : ChildrenVisitor {
    fun clear() {
        out.clear()
    }

    override operator fun invoke(mode: Node) {
        out += mode
    }
}

operator fun ChildrenVisitor.invoke(a: Node?) {
    if (a != null) {
        invoke(a)
    }
}

operator fun ChildrenVisitor.invoke(items: List<Node?>?) {
    if (items != null) for (it in items) this(it)
}

operator fun ChildrenVisitor.invoke(a: Node?, b: Node?) {
    this(a)
    this(b)
}

operator fun ChildrenVisitor.invoke(a: Node?, b: Node?, c: Node?) {
    this(a)
    this(b)
    this(c)
}

data class StructField(val name: String, var type: Type, val offset: Int, val size: Int, val node: StructDeclaration) {
    val offsetName = "OFFSET_$name"
}

data class StructTypeInfo(
    var name: String,
    //val spec: StructUnionTypeSpecifier,
    val spec: TypeSpecifier,
    val type: StructType,
    val struct: StructUnionTypeSpecifier,
    var size: Int = 0
) {
    private val _fieldsByName = LinkedHashMap<String, StructField>()
    private val _fields = ArrayList<StructField>()
    val fields: List<StructField> get() = _fields
    val fieldsByName: Map<String, StructField> get() = _fieldsByName
    fun addField(field: StructField) {
        _fields += field
        _fieldsByName[field.name] = field
    }
}

val ProgramParserRef.warnings: List<ProgramMessage> get() = parser.warnings
val ProgramParserRef.errors: List<ProgramMessage> get() = parser.errors
val ProgramParserRef.warningsAndErrors: List<ProgramMessage> get() = parser.warnings + parser.errors

inline fun <T> whileBlock(cond: (Int) -> Boolean, gen: () -> T): List<T> = arrayListOf<T>().apply { while (cond(size)) this += gen() }
inline fun <T> whileNotNull(gen: (Int) -> T?): List<T> = arrayListOf<T>().apply { while (true) this += gen(size) ?: break }

fun <T> ProgramParser.list(end: String, separator: String? = null, consumeEnd: Boolean = false, tailingSeparator: Boolean = false, gen: () -> T): List<T> {
    val out = arrayListOf<T>()
    if (peek() != end) {
        while (true) {
            if (out.size >= 16 * 1024) {
                error("Array too big")
            }
            out += gen()
            if (peek() == separator) {
                read()
                if (tailingSeparator) {
                    if (peek() == end) {
                        break
                    }
                }
                continue
            } else if (peek() == end) {
                break
            }
        }
    }
    if (consumeEnd) expect(end)
    return out
}

fun ProgramParser.identifier(): Id {
    val name = Id.validate(peek())
    val symbol: SymbolInfo? = intrinsics[name]?.toSymbol() ?: symbols[name]
    if (symbol == null) {
        reportWarning("Can't find identifier '$name'. Asumed as int.")
    }
    read()
    return Id(name, symbol)
}

fun ProgramParser.identifierDecl(): IdDecl {
    return IdDecl(read())
}

fun ProgramParser.primaryExpr(): Expr = tryPrimaryExpr() ?: TODO("primaryExpr: ${::primaryExpr.name}")

fun ProgramParser.tryPrimaryExpr(): Expr? = tag {
    when (val v = peek()) {
        "(" -> {
            expect("(")
            val expr = expression()
            expect(")")
            expr
        }
        "_Generic" -> {
            expect("_Generic", "(")
            TODO("_Generic")
            expect(")")
            TODO("_Generic")
        }
        else -> {
            when {
                Id.isValid(v) -> identifier()
                StringConstant.isValid(v) -> StringConstant(read().also { strings += it })
                CharConstant.isValid(v) -> CharConstant(read())
                IntConstant.isValid(v) -> IntConstant(read())
                DecimalConstant.isValid(v) -> DecimalConstant(read())
                else -> null
            }
        }
    }
}

//fun ProgramParser.tryExpression(): Expr? = tryBlock { expression() }

// (6.5.2) postfix-expression:
fun ProgramParser.tryPostFixExpression(): Expr? {
    var expr = tryPrimaryExpr() ?: return null
    loop@ while (!eof) {
        expr = when (peek()) {
            "[" -> {
                expect("[")
                val index = expression()
                expect("]")
                if (expr.type !is PointerType && expr.type !is ArrayType) {
                    reportWarning("Can't array-access a non-pointer type ${expr.type}")
                }
                ArrayAccessExpr(expr, index)
            }
            "(" -> {
                val exprType = expr.type
                // @TODO: Might be: ( type-name ) { initializer-list }
                if (exprType !is FunctionType) {
                    reportError("Not calling a function ($exprType) : ${expr.toString()}")
                }

                expect("(")
                //val args = list(")", ",") { expression() }
                val args = list(")", ",") { assignmentExpr() }
                expect(")")
                if (exprType is FunctionType) {
                    val func = exprType
                    val funcName = func.name
                    val funcParams = exprType.args
                    for (n in 0 until max(args.size, funcParams.size)) {
                        val exType = exprType.args.getOrNull(n)?.type
                        val arg = args.getOrNull(n)
                        if (arg == null) {
                            reportError("Expected parameter at $n for $funcName")
                        }
                        if ((arg != null) && (exType != null && !arg.type.canAssignTo(exType, this))) {
                            reportError("Can't assign ${arg.type} to $exType of parameter $n of $funcName")
                        }
                        if (exType == null && !func.variadic) {
                            reportError("Unexpected argument $n calling '$funcName'. Function only have ${funcParams.size} parameters and it is not variadic")
                        }
                    }
                }

                CallExpr(expr, args)
            }
            DOT, "->" -> {
                val indirect = read() == "->"

                val id = if (Id.isValid(peek())) identifierDecl() else {
                    reportError("Expected identifier after field access")
                    IdDecl("<unknown>")
                }
                val _type = expr.type

                val type = if (_type is PointerType) {
                    _type.elementType
                } else {
                    _type
                }
                val expectedIndirect = _type is PointerType || _type is ArrayType

                if (indirect != expectedIndirect) {
                    if (indirect) {
                        reportError("Expected . but found ->")
                    } else {
                        reportError("Expected -> but found .")
                    }
                }

                val resolvedType2 = type.resolve(parser)
                val resolvedType = if (resolvedType2 is BasePointerType) resolvedType2.elementType else resolvedType2

                val ftype = if (resolvedType is StructType) {
                    val struct = structTypesBySpecifier[resolvedType.spec]
                    if (struct != null) {
                        val ftype = struct.fieldsByName[id.name]?.type
                        if (ftype == null) {
                            reportWarning("Struct '$type' doesn't contain field '${id.name}'. Assumed as int.")
                        }
                        ftype
                    } else {
                        reportWarning("Can't find struct of ${resolvedType.spec.id} : ${structTypesByName.keys}. Assumed as int.")
                        null
                    }
                } else if (resolvedType is UnknownType) {
                    // For unknown types, infer field types based on common naming patterns
                    when (id.name) {
                        // Common stream/buffer fields
                        "total_in", "total_out", "avail_in", "avail_out", "total", "available", "avail" -> Type.ULONG
                        "next_in", "next_out", "next", "input", "output", "in_buf", "out_buf" -> PointerType(Type.UCHAR, false)
                        "msg", "message", "error_msg" -> PointerType(Type.CHAR, false)
                        "state", "context", "ctx" -> PointerType(Type.VOID, false)
                        "zalloc", "zfree", "alloc", "free", "malloc", "realloc" -> Type.VOID_PTR
                        "opaque", "user_data", "private_data", "data_ptr" -> Type.VOID_PTR
                        "data_type", "adler", "reserved", "type", "flags", "status", "mode" -> Type.ULONG

                        // Common file fields
                        "size", "pos", "offset", "length", "count", "bytes", "num_bytes", "capacity" -> Type.ULONG
                        "buffer", "data", "ptr", "buf", "memory", "mem", "content" -> PointerType(Type.UCHAR, false)
                        "handle", "file", "stream", "fp", "descriptor", "fd", "socket" -> Type.VOID_PTR

                        // Common counter and index fields
                        "index", "idx", "position", "counter", "count", "num", "number" -> Type.INT

                        // Common flag and status fields
                        "flag", "flags", "status", "state", "mode", "type", "options" -> Type.INT

                        // Common function pointer fields
                        "callback", "handler", "func", "function", "proc", "procedure" -> Type.VOID_PTR

                        // Default to int for other fields
                        else -> {
                            reportWarning("Can't find identifier '${id.name}'. Asumed as int.")
                            Type.INT
                        }
                    }
                } else {
                    reportWarning("Can't get field '${id.name}' from non struct type '$type'. Assumed as int.")
                    null
                }
                //println("$type: (${type::class})")
                FieldAccessExpr(expr, id, indirect, ftype ?: Type.INT, resolvedType)
            }
            "++", "--" -> {
                val op = read()
                PostfixExpr(expr, op)
            }
            else -> {
                break@loop
            }
        }
    }
    return expr
}

fun ProgramParser.tryUnaryExpression(): Expr? = tag {
    when (peek()) {
        "++", "--" -> {
            val op = read()
            val expr = tryUnaryExpression()
            Unop(op, expr!!)
        }
        "*" -> {
            expect("*")
            val expr = tryCastExpression() ?: parserException("Cast expression expected")
            ArrayAccessExpr(expr, IntConstant(0), isDeref = true)
        }
        "&", "+", "-", "~", "!" -> {
            val op = read()
            val expr = tryCastExpression() ?: parserException("Cast expression expected")
            if ((op == "+" || op == "-") && expr is NumericConstant) {
                when (op) {
                    "-" -> when (expr) {
                        is IntConstant -> IntConstant(-expr.value)
                        is DecimalConstant -> DecimalConstant(-expr.value, expr.type)
                        else -> Unop(op, expr)
                    }
                    else -> expr
                }
            } else {
                Unop(op, expr)
            }
        }
        "sizeof", "Alignof" -> {
            val kind = expectAny("sizeof", "Alignof")
            if (peek() == "(") {
                expect("(")
                val type = tryTypeName()
                val expr = if (type == null) tryUnaryExpression()!! else null
                expect(")")
                expr?.let { SizeOfAlignExprExpr(it) } ?: SizeOfAlignTypeExpr(kind, type!!)
            } else {
                tryUnaryExpression()!!
            }
        }
        else -> tryPostFixExpression()
    }
}

// Keep track of recursion depth to prevent stack overflow
private var castExpressionRecursionDepth = 0
private const val MAX_CAST_EXPRESSION_RECURSION_DEPTH = 10

fun ProgramParser.tryCastExpression(): Expr? = tag {
    // Check if we've exceeded the maximum recursion depth
    if (castExpressionRecursionDepth > MAX_CAST_EXPRESSION_RECURSION_DEPTH) {
        return tryUnaryExpression()
    }

    try {
        castExpressionRecursionDepth++

        if (peek() == "(") {
            try {
                val savedPos = pos

                expect("(")

                // Quick check for common tokens that indicate this is not a type name
                val currentToken = peek()
                if (currentToken == "*" || currentToken == "}" || currentToken == ";" || 
                    currentToken == "(" || currentToken == "=" || currentToken == "[" ||
                    currentToken.toIntOrNull() != null || currentToken in binaryOperators ||
                    currentToken in arrayOf(">=", "<=", "==", "!=", ">", "<")) {

                    // This is likely not a cast expression, restore position and try as unary expression
                    pos = savedPos
                    return@tag tryUnaryExpression()
                }

                val tname = tryTypeName()
                if (tname == null) {
                    // Not a type name, restore position and try as unary expression
                    pos = savedPos
                    return@tag tryUnaryExpression()
                }

                // Check if the next token is a comparison operator or other binary operator
                // If it is, this is not a cast expression but a parenthesized expression
                if (peek() in binaryOperators || peek() in arrayOf(">=", "<=", "==", "!=", ">", "<")) {
                    // Not a cast expression, restore position and try as unary expression
                    pos = savedPos
                    return@tag tryUnaryExpression()
                }

                expect(")")
                val expr = tryCastExpression()
                if (expr == null) {
                    // No expression after the cast, restore position and try as unary expression
                    pos = savedPos
                    return@tag tryUnaryExpression()
                }

                val ftype = tname.specifiers.toFinalType().withDeclarator(tname.abstractDecl)
                CastExpr(expr, ftype)
            } catch (e: Exception) {
                // If we encounter an error while parsing a cast expression,
                // fall back to treating it as a unary expression
                return@tag tryUnaryExpression()
            }
        } else {
            tryUnaryExpression()
        }
    } finally {
        castExpressionRecursionDepth--
    }
}

fun ProgramParser.tryBinopExpr(): Expr? = tag {
    val exprs = arrayListOf<Expr>()
    val ops = arrayListOf<String>()
    while (!eof) {
        exprs += tryCastExpression() ?: return null

        //println("PEEK AFTER EXPRESSION: ${peek()}")
        if (!eof && peek() in binaryOperators) {
            ops += read()
            continue
        } else {
            break
        }
    }

    if (exprs.size == 0) parserException("Not a expression! at $this")

    if (exprs.size == 1) {
        exprs.first()
    } else {
        BinOperatorsExpr(exprs, ops).expand()
    }
}

fun ProgramParser.tryConditionalExpr(): Expr? = tag {
    val expr = tryBinopExpr()
    if (expr != null && !eof && peek() == "?") {
        expect("?")
        val etrue = expression()
        expect(":")
        val efalse = tryConditionalExpr()!!
        TenaryExpr(expr, etrue, efalse)
    } else {
        expr
    }
}

// Right associativity
fun ProgramParser.tryAssignmentExpr(): Expr? = tag {
    val left = tryConditionalExpr() ?: return null
    return if (!eof && peek() in assignmentOperators) {
        val op = read()
        val right = tryAssignmentExpr() ?: parserException("Expected value after assignment")
        if (!right.type.canAssignTo(left.type, this)) {
            reportWarning("Can't assign ${right.type} to ${left.type} (${right.type.resolve(parser)} != ${left.type.resolve(parser)})")
        }
        AssignExpr(left, op, right).toSimpleAssignExpr()
    } else {
        left
    }
}

fun ProgramParser.assignmentExpr(): Expr = tryAssignmentExpr() ?: parserException("Not an assignment-expression at $this")

// @TODO: Support comma separated
fun ProgramParser.tryExpression(): Expr? {
    val exprs = arrayListOf<Expr>()
    while (!eof) {
        exprs += tryAssignmentExpr() ?: break
        if (peekOutside() == ",") {
            read()
            continue
        } else {
            break
        }
    }
    return when {
        exprs.isEmpty() -> null
        exprs.size == 1 -> exprs.first()
        else -> CommaExpr(exprs)
    }
}

fun ProgramParser.expression(): Expr = tryExpression() ?: parserException("Not an expression at $this")

fun ProgramParser.constantExpression(): ConstExpr {
    return ConstExpr(tryConditionalExpr() ?: error("Not a conditional-expression")) // @TODO: Validate it is constant
}

fun ProgramParser.stringLiteral(): ConstExpr {
    return ConstExpr(expression()) // @TODO: Validate it is constant
}

private inline fun <T : Node?> ProgramParser.tag(callback: () -> T): T {
    val startPos = this.pos
    return callback().also {
        if (it?.tagged != true) {
            it?.tagged = true
            it?.pos = startPos
            it?.endPos = pos
            val tokens = this.tokens
            it?.comment = buildString {
                append(tokens.getOrNull(startPos)?.comment ?: "")
                /*
                for (n in startPos + 1 until pos) {
                    val comment = tokens.getOrNull(n)?.comment ?: continue
                    if (comment != "") {
                        append(comment)
                    }
                }
                 */

            }
            if (it?.func == null) {
                it?.func = _functionScope
            }
        }
    }
}

//private fun <T : Any> TokenReader.multiple(report: (Throwable) -> Unit = { }, callback: () -> T): List<T> {
//    val out = arrayListOf<T>()
//    while (!eof) {
//        out += tryBlock { callback() } ?: break
//        //val result = tryBlockResult { callback() }
//        //if (result.isError) {
//        //    //report(result.error)
//        //    break
//        //} else {
//        //    out += result.value
//        //}
//    }
//    return out
//}

//fun <T : Any> TokenReader.multipleWithSeparator(callback: () -> T, separator: () -> Unit): List<T> {
//    val out = arrayListOf<T>()
//    while (true) {
//        out += tryBlock { callback() } ?: break
//        tryBlock { separator() } ?: break
//    }
//    return out
//}

fun ProgramParser.blockItem(): Stm = tag {
    when (peek()) {
        // Do not try declarations on these
        "if", "switch", "while", "do", "for", "goto", "continue", "break", "return", "{", "case", "default" -> statement()
        else -> {
            // Check if this is likely a statement with an arrow operator or array access
            if (Id.isValid(peek()) && (peekOutside(+1) == "->" || peekOutside(+1) == "[" || peekOutside(+1) == "++")) {
                statement()
            } else {
                // Try to recover from parsing errors by prioritizing statements in more cases
                try {
                    tryBlocks("block-item", this, { declaration(sure = true) }, { statement() })
                } catch (e: ParserException) {
                    // If parsing as a declaration fails, try parsing as a statement
                    statement()
                }
            }
        }
    }
}

fun ProgramParser.statement(): Stm = tag {
    consumeLineMarkers()
    when (peek()) {
        // (6.8.4) selection-statement:
        "if" -> {
            expect("if", "(")
            val expr = expression()
            expect(")")
            val strue = statement()
            val sfalse = if (tryExpect("else") != null) statement() else null
            IfElse(expr, strue, sfalse)
        }
        "switch" -> {
            expect("switch", "(")
            val expr = expression()
            expect(")")
            val body = compoundStatement()
            Switch(expr, body)
        }
        // (6.8.5) iteration-statement:
        "while" -> {
            expect("while", "(")
            val expr = expression()
            expect(")")
            val body = statement()
            While(expr, body)
        }
        "do" -> {
            expect("do")
            val body = statement()
            expect("while")
            expect("(")
            val expr = expression()
            expect(")")
            expect(";")
            DoWhile(body, expr)
        }
        "for" -> {
            expect("for", "(")
            //val init = expressionOpt()
            //expect(";")

            val initDecl = tryDeclaration()
            val init = if (initDecl == null) {
                val expr = tryExpression()
                expect(";")
                expr
            } else {
                initDecl
            }
            val cond = tryExpression()
            expect(";")
            val post = tryExpression()
            expect(")")
            val body = statement()
            For(init, cond, post, body)
        }
        // (6.8.6) jump-statement:
        "goto" -> run {
            _functionScope?.hasGoto = true; expect("goto");
            val id = identifierDecl(); expect(";"); Goto(id)
        }
        "continue" -> run { expect("continue", ";"); Continue() }
        "break" -> run { expect("break", ";"); Break() }
        "return" -> {
            expect("return")
            val expr = tryExpression()
            //println(functionScope.type)
            //println(functionScope.rettype)
            when {
                expr == null && functionScope.rettype != Type.VOID -> reportError("Return must return ${functionScope.rettype}")
                expr != null && !expr.type.canAssignTo(
                    functionScope.rettype,
                    this
                ) -> reportError(
                    "Returned ${expr.type} but must return ${functionScope.rettype} (${expr.type.resolve(parser)} != ${_functionScope?.rettype?.resolve(
                        parser
                    )})"
                )
            }
            expect(";")
            Return(expr)
        }
        // (6.8.2) compound-statement:
        "{" -> compoundStatement()
        // (6.8.1) labeled-statement:
        "case", "default" -> {
            val isDefault = peek() == "default"
            read()
            val expr = if (isDefault) null else constantExpression()
            expect(":")
            val stms = arrayListOf<Stm>()
            while (!eof) {
                if (peek() == "case") break
                if (peek() == "default") break
                if (peek() == "}") break
                stms += blockItem()
            }
            val stm = Stms(stms)
            if (expr != null) {
                CaseStm(expr, stm)
            } else {
                DefaultStm(stm)
            }
        }
        // (6.8.3) expression-statement:
        else -> {
            val result = tryBlocks("expression-statement", this,
                // (6.8.1) labeled-statement:
                {
                    val id = identifierDecl()
                    expect(":")
                    val stm = statement()
                    LabeledStm(id, stm)
                },
                {
                    val expr = tryExpression()
                    if (peekOutside() != ";") {
                        reportError("Expected ; after expression")
                        when (peekOutside()) {
                            in keywords -> Unit
                            "}" -> Unit
                            else -> skip()
                        }
                    } else {
                        expect(";")
                    }
                    ExprStm(expr)
                }
            )
            result
        }
    }
}

interface KeywordEnum {
    val keyword: String

    open class Companion<T : KeywordEnum>(val gen: () -> Array<T>) {
        val BY_KEYWORD = gen().associateBy { it.keyword }
        operator fun get(keyword: String) = BY_KEYWORD[keyword] ?: error("Can't find enum entry with keyword '$keyword'")
    }
}

// (6.7.7) type-name:
fun ProgramParser.typeName(): Node = tryTypeName() ?: TODO("typeName as $this")

// Keep track of recursion depth to prevent stack overflow
private var typeNameRecursionDepth = 0
private const val MAX_TYPE_NAME_RECURSION_DEPTH = 10

fun ProgramParser.tryTypeName(): TypeName? = tag {
    // Check if we've exceeded the maximum recursion depth
    if (typeNameRecursionDepth > MAX_TYPE_NAME_RECURSION_DEPTH) {
        return null
    }

    try {
        typeNameRecursionDepth++

        // Check for tokens that indicate this is not a type name
        val currentToken = peek()
        if (currentToken == "*" || currentToken == "}" || currentToken == ";" || 
            currentToken == "(" || currentToken == "=" || currentToken == "[") {
            return null
        }

        val specifiers = declarationSpecifiers() ?: return null
        val absDecl = tryAbstractDeclarator()
        TypeName(specifiers, absDecl)
    } catch (e: Exception) {
        // If we encounter an error while parsing a type name in a preprocessor directive,
        // return null to indicate that this is not a valid type name
        if (e.message?.contains("Expected") == true) {
            return null
        }
        throw e
    } finally {
        typeNameRecursionDepth--
    }
}

fun ProgramParser.tryDirectAbstractDeclarator(): Node? {
    var out: Node? = null
    loop@ while (true) {
        val current = out
        out = when (peek()) {
            "(" -> {
                expect("(")
                val adc = tryAbstractDeclarator()
                expect(")")
                adc
            }
            "[" -> {
                expect("[")
                // Parse type qualifiers and static keywords
                val typeQualifiers = arrayListOf<TypeQualifier>()
                var static0 = false
                var static1 = false

                while (true) {
                    if (peek() == "static") {
                        read() // Consume "static"
                        if (!static0) static0 = true
                        else static1 = true
                    } else {
                        val qualifier = tryTypeQualifier()
                        if (qualifier != null) {
                            typeQualifiers.add(qualifier)
                        } else {
                            break
                        }
                    }
                }

                val expr = if (peek() != "]") tryExpression() else null
                expect("]")

                // If we have a base declarator, create an ArrayDeclarator
                if (current is Declarator) {
                    ArrayDeclarator(current, typeQualifiers, expr, static0, static1)
                } else {
                    // Create a placeholder declarator for abstract array declarators
                    val base = IdentifierDeclarator(IdDecl(""))
                    ArrayDeclarator(base, typeQualifiers, expr, static0, static1)
                }
            }
            else -> break@loop
        }
    }
    return out
}

fun ProgramParser.tryAbstractDeclarator(): AbstractDeclarator? = tag {
    val pointer = tryPointer()
    val adc = tryDirectAbstractDeclarator()
    if (pointer == null && adc == null) return null
    AbstractDeclarator(pointer, adc)
}

// (6.7) declaration-specifiers:
fun ProgramParser.declarationSpecifiers(sure: Boolean = false): ListTypeSpecifier? {
    consumeLineMarkers()
    val out = arrayListOf<TypeSpecifier>()
    var hasTypedef = false
    var errorCount = 0
    var inStructDecl = false // Flag to track if we're inside a struct declaration
    val maxErrors = 5 // Maximum number of errors before giving up

    // Check if we're inside a struct declaration by looking at previous tokens
    val prevTokens = tokens.subList(max(0, pos - 10), pos).map { it.str }
    if (prevTokens.contains("struct") || prevTokens.contains("union")) {
        inStructDecl = true
    }

    // Check for tokens that indicate we're not in a declaration specifier context
    val currentToken = peek()

    // Early detection of problematic tokens that are not declaration specifiers
    when (currentToken) {
        // Pointer (*) token - this is part of a declarator, not a declaration specifier
        "*" -> {
            // Instead of just warning, try to handle pointer declarations more intelligently
            // Look ahead to see if this might be a function pointer declaration
            val nextTokens = (1..5).map { peekOutside(it) }.takeWhile { it != null && it != ";" }

            // Check for function pointer pattern: * identifier ( ... )
            if (nextTokens.size >= 3 && Id.isValid(nextTokens[0]) && nextTokens[1] == "(") {
                // This looks like a function pointer declaration
                // Add a void type as the base type for the pointer
                out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.VOID))
                return ListTypeSpecifier(out)
            }

            // Check for simple pointer pattern: * identifier
            if (nextTokens.isNotEmpty() && Id.isValid(nextTokens[0])) {
                // This looks like a simple pointer declaration
                // Add a void type as the base type for the pointer
                out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.VOID))
                return ListTypeSpecifier(out)
            }

            // Default fallback
            reportWarning("Found pointer (*) at start of declaration, assuming 'void'")
            out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.VOID))
            return ListTypeSpecifier(out)
        }
        // Closing brace (}) token - this is the end of a struct/union/enum definition
        "}" -> {
            // If we're in a struct declaration, this is expected
            if (inStructDecl) {
                // Just return null to indicate end of struct declaration
                return null
            }

            // Otherwise, assume int and continue
            reportWarning("Found closing brace (}) at start of declaration, assuming 'int'")
            out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))
            return ListTypeSpecifier(out)
        }
        // Other tokens that might indicate we're not in a declaration specifier context
        ")", "]", "," -> {
            // Check if we're in a parameter list or array declaration
            if (prevTokens.contains("(") || prevTokens.contains("[")) {
                // This is expected in parameter lists or array declarations
                // Just return null to indicate end of declaration
                return null
            }

            // Otherwise, assume int and continue
            reportWarning("Found unexpected token '$currentToken' at start of declaration, assuming 'int'")
            out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))
            return ListTypeSpecifier(out)
        }
    }

    while (true) {
        if (eof) error("eof found")

        // Check for common tokens that might indicate the end of declaration specifiers
        val currentToken = peek()

        // Break on tokens that indicate the end of declaration specifiers
        if (currentToken == ";" || currentToken == "(" || 
            currentToken == "=" || currentToken == "[" ||
            currentToken == "*" || currentToken == "}" ||
            currentToken == ")" || currentToken == "]" ||
            currentToken == ",") {

            // If we have no specifiers yet, add a default int type
            if (out.isEmpty()) {
                // Choose appropriate default type based on context
                if (currentToken == "*") {
                    // For pointers, void is often a better default
                    reportWarning("No valid declaration specifiers found before '$currentToken', assuming 'void'")
                    out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.VOID))
                } else {
                    reportWarning("No valid declaration specifiers found before '$currentToken', assuming 'int'")
                    out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))
                }
            }
            break
        }

        try {
            val spec = tryDeclarationSpecifier(hasTypedef, out.isNotEmpty(), sure) ?: break
            if (spec is StorageClassSpecifier && spec.kind == StorageClassSpecifier.Kind.TYPEDEF) hasTypedef = true
            out += spec
            errorCount = 0 // Reset error count on successful parse
        } catch (e: Exception) {
            // If we encounter an error while parsing a declaration specifier,
            // report a warning and try to recover
            errorCount++
            reportWarning("Can't declarationSpecifiers ${this} - ${e.message}")

            // If we've had too many errors in a row, break out to avoid infinite loops
            if (errorCount >= maxErrors) {
                reportWarning("Too many errors in declaration specifiers, giving up")
                // If we have no specifiers yet, add a default int type
                if (out.isEmpty()) {
                    out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))
                }
                break
            }

            // Check for tokens that definitely indicate we should stop parsing declaration specifiers
            val currentToken = peek()
            if (currentToken == ";" || currentToken == "(" || 
                currentToken == "=" || currentToken == "[" ||
                currentToken == "*" || currentToken == "}" ||
                currentToken == ")" || currentToken == "]" ||
                currentToken == ",") {

                // If we have no specifiers yet, add a default int type
                if (out.isEmpty()) {
                    // Choose appropriate default type based on context
                    if (currentToken == "*") {
                        // For pointers, void is often a better default
                        reportWarning("No valid declaration specifiers found before '$currentToken', assuming 'void'")
                        out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.VOID))
                    } else {
                        reportWarning("No valid declaration specifiers found before '$currentToken', assuming 'int'")
                        out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))
                    }
                }
                break
            }

            // Otherwise, skip this token and continue
            read()
        }
    }

    // If we have no specifiers but we're not at the end of input, try to create a basic type
    if (out.isEmpty() && !eof) {
        // Add a basic int type as a fallback
        reportWarning("No valid declaration specifiers found, assuming 'int'")
        out.add(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))
    }

    return if (out.isEmpty()) null else ListTypeSpecifier(out)
}

fun ProgramParser.type(): ListTypeSpecifier = tag {
    return declarationSpecifiers()!!
    //NamedCType(identifier())
}

fun ProgramParser.tryTypeQualifier(): TypeQualifier? = tag {
    when (peek()) {
        "const", "restrict", "volatile", "_Atomic" -> TypeQualifier(TypeQualifier.Kind[read()])
        else -> null
    }
}

//fun ProgramParser.trySpecifierQualifier() = tag {
//    when (val v = peek()) {
//
//        "const", "restrict", "volatile", "_Atomic" -> TypeQualifier(v)
//        else -> null
//    }
//
//}

// (6.7.2.1) struct-declarator:
fun ProgramParser.structDeclarator(): StructDeclarator = tryStructDeclarator() ?: error("Not a struct declarator!")

fun ProgramParser.tryStructDeclarator(): StructDeclarator? = tag {
    val declarator = tryDeclarator()
    val bitExpr = if (declarator == null || peek() == ":") {
        if (declarator == null && peek() != ":") return@tag null
        expect(":")
        constantExpression()
    } else {
        null
    }
    StructDeclarator(declarator, bitExpr)
}

// (6.7.2.1) struct-declaration:
fun ProgramParser.tryStructDeclaration(): StructDeclaration? = tag {
    return if (peek() == "_Static_assert") {
        staticAssert()
    } else {
        val specifiers = declarationSpecifiers() // DISALLOW others
        val declarators = list(";", ",") { structDeclarator() }
        expect(";")
        StructDeclaration(specifiers ?: error("$specifiers $declarators at $this"), declarators)
    }
}

fun ProgramParser.enumerator(): EnumItemDef = tag {
    val id = identifierDecl()
    val expr = if (peek() == "=") {
        expect("=")
        constantExpression()
    } else {
        null
    }
    EnumItemDef(id, expr)
}

fun ProgramParser.tryDeclarationSpecifier(hasTypedef: Boolean, hasMoreSpecifiers: Boolean, sure: Boolean = false): TypeSpecifier? = tag {
    when (val v = peek()) {
        "typedef", "extern", "static", "_Thread_local", "auto", "register" -> StorageClassSpecifier(StorageClassSpecifier.Kind[read()]!!)
        "const", "restrict", "volatile" -> {
            val kind = TypeQualifier.Kind[read()]
            if (kind == TypeQualifier.Kind.ATOMIC && peekOutside() == "(") {
                expect("(")
                TODO("_Atomic")
                val name = typeName()
                expect(")")
                AtomicTypeSpecifier(name)
            }
            TypeQualifier(kind)
        }
        "inline", "_Noreturn" -> FunctionSpecifier(read())
        "_Alignas" -> {
            expect("_Alignas")
            expect("(")
            val node = tryTypeName() ?: constantExpression()
            expect(")")
            AlignAsSpecifier(node)
        }
        "void", "char", "short", "int", "long", "float", "double", "signed", "unsigned", "_Bool", "_Complex" -> {
            BasicTypeSpecifier(BasicTypeSpecifier.Kind[read()])
        }
        "enum" -> {
            val kind = read()
            val id = if (Id.isValid(peek())) read() else null
            val decls = if (peek() == "{") {
                expect("{")
                val enums = this.list("}", ",") { enumerator() }
                expect("}")
                enums
            } else {
                null
            }
            EnumTypeSpecifier(id, decls)
        }
        "struct", "union" -> {
            val kind = read()
            val id = if (peek() != "{") identifierDecl() else null
            val decls = if (peek() == "{") {
                expectPair("{", "}") {
                    list("}", null) { tryStructDeclaration() ?: error("No a struct-declaration") }
                }
            } else {
                null
            }

            // Analyzer
            if (decls != null) { // { ... }
                val struct = StructUnionTypeSpecifier(kind, id, decls)
                val it = struct
                val isUnion = struct.kind == "union"
                val structName = it.id?.name ?: "Anonymous${structId++}"
                val structInfo = StructTypeInfo(structName, it, StructType(it), struct)
                struct.info = structInfo
                structTypesByName[structName] = structInfo
                structTypesBySpecifier[it] = structInfo
                var offset = 0
                var maxSize = 0
                for (decl in it.decls) {
                    val ftype = decl.specifiers.toFinalType()
                    for (dtors in decl.declarators) {
                        val name = dtors.declarator?.getName() ?: "unknown"
                        val rftype = ftype.withDeclarator(dtors.declarator)
                        val rsize = rftype.getSize(parser)
                        structInfo.addField(StructField(name, rftype, offset, rsize, decl))
                        maxSize = kotlin.math.max(maxSize, rsize)
                        if (!isUnion) {
                            offset += rsize
                        }
                    }
                }
                structInfo.size = if (isUnion) maxSize else offset
                struct
            } else {
                val structType = structTypesByName[id?.name]
                val struct = structType?.struct ?: StructUnionTypeSpecifier(kind, id, listOf())
                struct
            }
        }
        // Handle special macros and types that might appear in declaration specifiers
        // These are typically compiler-specific or library-specific attributes
        // that don't affect the actual type
        v -> when {
            // Skip compiler-specific or library-specific macros and attributes
            // Using a general approach that works for any library
            v.startsWith("__") || v.endsWith("__") || v.endsWith("INTERNAL") || 
            v == "FAR" || v.contains("EXPORT") || v.contains("EXTERN") || 
            v.startsWith("_") && v[1].isUpperCase() || // Common pattern for library macros (_Z, _WIN, etc.)
            v.length >= 2 && v[0].isUpperCase() && v[1].isUpperCase() -> { // All-caps macros
                // Skip these macros and continue parsing
                read() // Consume the macro
                // Try to parse the next token as a declaration specifier
                tryDeclarationSpecifier(hasTypedef, hasMoreSpecifiers, sure)
            }
            // Handle identifiers that are likely to be types based on naming conventions
            // This is a general approach that works for any library
            Id.isValid(v) && !v.equals("struct") && !v.equals("union") && !v.equals("enum") -> {
                // Handle special types using naming conventions
                val typeName = read() // Consume the type name

                // Determine the appropriate type based on naming conventions
                val type = when {
                    // Handle unsigned types with various naming patterns
                    v.startsWith("u") && v.length > 1 && (v[1].isUpperCase() || v[1].isDigit() || v.substring(1).startsWith("int") || v.substring(1).startsWith("long")) -> {
                        if (v.contains("Long") || v.contains("long") || v.endsWith("L") || v.endsWith("l")) {
                            Type.ULONG
                        } else if (v.contains("Int") || v.contains("int")) {
                            Type.UINT
                        } else if (v.contains("Char") || v.contains("char") || v.contains("Byte") || v.contains("byte")) {
                            Type.UCHAR
                        } else {
                            Type.UINT // Default for unsigned types
                        }
                    }
                    // Handle pointer types with various naming patterns
                    v.endsWith("p") || v.endsWith("ptr") || v.endsWith("Ptr") || 
                    v.contains("_ptr") || v.contains("_p") || v.contains("Pointer") || 
                    v.endsWith("pc") || v.endsWith("pf") || 
                    v.contains("stream") || v.contains("Stream") || 
                    v.contains("File") || v.contains("file") || 
                    v.endsWith("_s") || v.endsWith("_state") || 
                    v.contains("handle") || v.contains("Handle") -> {
                        Type.VOID_PTR
                    }
                    // Handle function pointer types with various naming patterns
                    v.endsWith("_func") || v.endsWith("_callback") || v.endsWith("_fn") || 
                    v.contains("_func_") || v.contains("_callback_") || v.contains("_fn_") || 
                    v.startsWith("alloc_") || v.startsWith("free_") || v.contains("_alloc") || v.contains("_free") ||
                    v.endsWith("_f") || v.endsWith("_t") && (v.contains("func") || v.contains("callback")) -> {
                        Type.VOID_PTR
                    }
                    // Handle common type names
                    v == "size_t" || v == "SIZE_T" || v.endsWith("_t") && v.contains("size") -> {
                        Type.UINT
                    }
                    v.contains("off_t") || v.contains("off64_t") || v.contains("offset") || v.contains("Offset") -> {
                        Type.LONG
                    }
                    v == "va_list" || v.contains("va_list") -> {
                        Type.VA_LIST
                    }
                    // Special handling for types ending with 'f' (often indicates a pointer type)
                    v.endsWith("f") && v.length > 1 -> {
                        if (v.contains("Byte") || v.contains("byte") || v.contains("char")) {
                            PointerType(Type.UCHAR, false)
                        } else if (v.contains("int") || v.contains("Int")) {
                            PointerType(Type.INT, false)
                        } else if (v.contains("long") || v.contains("Long")) {
                            PointerType(Type.LONG, false)
                        } else {
                            PointerType(Type.VOID, false) // Default to void pointer
                        }
                    }
                    // Handle types based on name components
                    v.contains("char") -> Type.CHAR
                    v.contains("uchar") || v.contains("byte") || v.contains("Byte") -> Type.UCHAR
                    v.contains("int") -> {
                        if (v.startsWith("u") || v.startsWith("U")) {
                            Type.UINT
                        } else {
                            Type.INT
                        }
                    }
                    v.contains("long") -> {
                        if (v.startsWith("u") || v.startsWith("U")) {
                            Type.ULONG
                        } else {
                            Type.LONG
                        }
                    }
                    v.contains("float") || v.contains("Float") -> Type.FLOAT
                    v.contains("double") || v.contains("Double") -> Type.DOUBLE
                    v.contains("bool") || v.contains("Bool") -> Type.BOOL
                    // Handle types ending with _t (common C typedef pattern)
                    v.endsWith("_t") -> {
                        if (v.contains("int") || v.contains("Int")) {
                            Type.INT
                        } else if (v.contains("long") || v.contains("Long")) {
                            Type.LONG
                        } else if (v.contains("char") || v.contains("Char")) {
                            Type.CHAR
                        } else {
                            Type.INT // Default for _t types
                        }
                    }
                    // Default to int for other types
                    else -> Type.INT
                }

                RefTypeSpecifier(typeName, type)
            }
            else -> null
        }?.let { return@tag it }
        else -> when {
            v in typedefTypes -> {
                val typeName = read()
                RefTypeSpecifier(typeName, typedefAliases[typeName] ?: Type.UNKNOWN_TYPEDEF)
            }
            //hasTypedef && Id.isValid(v) -> TypedefTypeSpecifierName(read())
            else -> when {
                hasMoreSpecifiers -> null // @TODO: check?
                sure -> throw ExpectException("'$v' is not a valid type")
                else -> null
            }
        }
    }
}

fun ProgramParser.tryPointer(): Pointer? = tag {
    var pointer: Pointer? = null
    while (true) {
        // Check for pointer token
        if (peek() == "*") {
            expect("*")
            // Create a new pointer with any type qualifiers that follow
            pointer = Pointer(whileNotNull { tryTypeQualifier() }, pointer)
        } 
        // Handle special case for function pointers in struct declarations
        else if (peek() == "(" && peekOutside(+1) == "*") {
            // This is likely a function pointer declaration in a struct
            // We'll handle this by creating a special pointer type
            expect("(")
            expect("*")
            // Collect any type qualifiers
            val qualifiers = whileNotNull { tryTypeQualifier() }
            // Check for closing parenthesis
            if (peek() == ")") {
                expect(")")
                // Create a pointer with the collected qualifiers
                pointer = Pointer(qualifiers, pointer)
            } else {
                // If no closing parenthesis, revert and break
                reportWarning("Expected closing parenthesis for function pointer")
                break
            }
        } else {
            break
        }
    }
    pointer
}

// (6.7.6) parameter-declaration:
fun ProgramParser.parameterDeclaration(): ParameterDecl = tag {
    if (peek() == "...") {
        val id = tag { IdDecl(read()) }
        ParameterDecl(ListTypeSpecifier(listOf(VariadicTypeSpecifier(id))), VarargDeclarator(IdentifierDeclarator(id)))
    } else {
        // Skip known macros that might appear in parameter declarations
        val skippedTokens = arrayListOf<String>()
        while (!eof) {
            val token = peek()
            // Skip compiler/library-specific macros and type qualifiers
            if (token.startsWith("Z") || token.endsWith("INTERNAL") || 
                token == "FAR" || token.endsWith("_const") || token == "const") {
                skippedTokens.add(read())
            } else {
                break
            }
        }

        val specs = declarationSpecifiers()
        if (specs == null) {
            // Special handling for common types that might not be recognized
            val token = peek()
            if (token == "va_list") {
                read() // Consume va_list
                val vaListSpecs = ListTypeSpecifier(listOf(RefTypeSpecifier("va_list", Type.VA_LIST)))

                // Try to read the parameter name
                val paramName = if (Id.isValid(peek())) {
                    identifierDecl()
                } else {
                    IdDecl("va")
                }

                return@tag ParameterDecl(vaListSpecs, IdentifierDeclarator(paramName))
            } else if (token.endsWith("p") || token.contains("stream") || token.contains("File") || 
                      token.endsWith("_t") || token.contains("Byte") || token.contains("pc") || 
                      token.contains("_ptr")) {
                // Handle special types based on naming conventions
                val typeName = read() // Consume the type name
                // Determine the appropriate type based on naming conventions
                val type = when {
                    // Handle types with naming patterns in a general way
                    // Handle types based on naming conventions in a general way
                    typeName.contains("Byte") && !typeName.endsWith("f") -> Type.UCHAR // Byte pattern indicates unsigned char
                    typeName.endsWith("f") -> {
                        // *f suffix often indicates a pointer type
                        if (typeName.contains("Byte") || typeName.contains("byte") || typeName.contains("char")) {
                            PointerType(Type.UCHAR, false) // Pointer to byte/char
                        } else if (typeName.contains("int") || typeName.contains("Int")) {
                            PointerType(Type.INT, false) // Pointer to int
                        } else if (typeName.contains("long") || typeName.contains("Long")) {
                            PointerType(Type.LONG, false) // Pointer to long
                        } else {
                            PointerType(Type.VOID, false) // Default to void pointer
                        }
                    }
                    typeName.endsWith("p") || typeName.endsWith("ptr") || typeName.contains("_ptr") -> Type.VOID_PTR // Pointer type
                    typeName.endsWith("_t") -> {
                        // Common C typedef pattern
                        if (typeName.contains("size") || typeName.contains("Size")) {
                            Type.UINT // size_t is usually unsigned int
                        } else if (typeName.contains("int") || typeName.contains("Int")) {
                            Type.INT
                        } else if (typeName.contains("long") || typeName.contains("Long")) {
                            Type.LONG
                        } else if (typeName.contains("char") || typeName.contains("Char")) {
                            Type.CHAR
                        } else {
                            Type.INT // Default for _t types
                        }
                    }
                    typeName.contains("stream") || typeName.contains("Stream") || 
                    typeName.contains("File") || typeName.contains("file") -> Type.VOID_PTR // Stream or file type
                    else -> Type.VOID_PTR // Default to void pointer for other types
                }
                val typeSpec = RefTypeSpecifier(typeName, type) // Use the appropriate type
                val specs = ListTypeSpecifier(listOf(typeSpec))

                // Try to read the parameter name
                val paramName = if (Id.isValid(peek())) {
                    identifierDecl()
                } else {
                    IdDecl("param_" + typeName)
                }

                return@tag ParameterDecl(specs, IdentifierDeclarator(paramName))
            } else {
                reportError("Expected declaration specifiers at $this")
            }
        }

        // Try to parse a declarator, but handle the case where there's no declarator
        val decl = try {
            declarator()
        } catch (e: ExpectException) {
            // If we can't parse a declarator, create an anonymous one
            IdentifierDeclarator(IdDecl("anonymous_param"))
        }

        ParameterDecl(specs ?: ListTypeSpecifier(listOf()), decl)
    }
}

// (6.7.6) declarator:
// (6.7.6) direct-declarator:
fun ProgramParser.declarator(): Declarator = tryDeclarator()
    ?: throw ExpectException("Not a declarator at $this")

fun ProgramParser.tryDeclarator(): Declarator? = tag {
    // Check if we're inside a struct declaration by looking at previous tokens
    val prevTokens = tokens.subList(max(0, pos - 10), pos).map { it.str }
    val inStructDecl = prevTokens.contains("struct") || prevTokens.contains("union")

    // General case for function pointer typedefs and special types
    if (pos > 0 && (tokens[pos-1].str == "typedef" || inStructDecl) && Id.isValid(peek())) {
        // Check if the identifier looks like a function pointer typedef or special type
        // Using a more general approach that works for any library
        val identifier = peek()
        if (identifier.endsWith("_func") || identifier.endsWith("_callback") || identifier.endsWith("_fn") ||
            identifier.contains("_func_") || identifier.contains("_callback_") || identifier.contains("_fn_") ||
            identifier.endsWith("File") || identifier.endsWith("_file") || 
            identifier.endsWith("_t") || identifier.endsWith("_type") ||
            identifier.endsWith("p") || identifier.endsWith("ptr") || identifier.endsWith("Ptr") ||
            identifier.endsWith("_state") || identifier.endsWith("State") ||
            identifier.endsWith("_s") || identifier.endsWith("_struct") ||
            identifier.contains("_ptr") || identifier.contains("Pointer") ||
            identifier.contains("stream") || identifier.contains("Stream") ||
            identifier.endsWith("f") && identifier.length > 1 || // *f suffix often indicates a pointer type
            identifier.endsWith("_h") || identifier.endsWith("_handle") || identifier.contains("Handle") ||
            identifier.startsWith("alloc_") || identifier.startsWith("free_") ||
            identifier.contains("_alloc") || identifier.contains("_free")) {
            val id = IdentifierDeclarator(identifierDecl())
            return@tag id
        }
    }

    // Special case for complex function pointer typedefs or struct fields
    if (pos > 0 && (tokens[pos-1].str == "typedef" || inStructDecl)) {
        // Check if we're in a complex function pointer typedef like:
        // typedef unsigned (*function_name)(void *, unsigned, unsigned);
        // or a function pointer field in a struct like:
        // alloc_func zalloc;
        val startPos = pos

        // Skip type specifiers (return type)
        var typeSpecifierFound = false
        while (peek() == "unsigned" || peek() == "void" || peek() == "int" || peek() == "char" || 
               peek() == "long" || peek() == "const" || peek() == "alloc_func" || peek() == "free_func" ||
               peek().endsWith("_func") || peek().endsWith("_callback") || peek().endsWith("_fn") ||
               peek().contains("_func_") || peek().contains("_callback_") || peek().contains("_fn_")) {
            read()
            typeSpecifierFound = true
        }

        // If we found type specifiers and next is a function pointer pattern
        if (typeSpecifierFound && peek() == "(" && peekOutside(+1) == "*") {
            expect("(")
            expect("*")

            // Read the function name if present
            val funcName = if (Id.isValid(peek())) {
                read()
            } else {
                "anonymous_func"
            }

            expect(")")

            // Skip the parameter list
            if (peek() == "(") {
                expect("(")
                var parenCount = 1
                while (!eof && parenCount > 0) {
                    val token = read()
                    if (token == "(") parenCount++
                    else if (token == ")") parenCount--
                }
            }

            // Skip any remaining parts of the declaration until semicolon
            while (!eof && peek() != ";") {
                read()
            }

            return@tag IdentifierDeclarator(IdDecl(funcName))
        } else if (typeSpecifierFound && Id.isValid(peek())) {
            // This might be a function pointer field in a struct like:
            // alloc_func zalloc;
            val id = IdentifierDeclarator(identifierDecl())
            return@tag id
        } else {
            // Rewind if this wasn't a function pointer typedef or field
            pos = startPos
        }
    }

    val pointer = tryPointer()
    val base: Declarator = tag {
        when (peek()) {
            // ( declarator ) or ( *identifier ) for function pointers
            "(" -> {
                expect("(")
                // Check if this is a function pointer declaration like (*func)
                if (peek() == "*") {
                    val innerPointer = tryPointer()
                    if (innerPointer != null && Id.isValid(peek())) {
                        val id = IdentifierDeclarator(identifierDecl())
                        expect(")")
                        // Instead of immediately returning, store the function pointer declarator
                        val funcPtrDecl = DeclaratorWithPointer(innerPointer, id)

                        // Check if this is followed by a parameter list (function pointer typedef)
                        if (peek() == "(") {
                            // Process parameter list for function pointer
                            expect("(")
                            val params = if (peekOutside() == "void" && peekOutside(+1) == ")") {
                                expect("void")
                                listOf()
                            } else {
                                try {
                                    list(")", ",") { parameterDeclaration() }
                                } catch (e: ExpectException) {
                                    // If parameter parsing fails, skip to closing parenthesis
                                    var depth = 1
                                    while (depth > 0 && !eof) {
                                        val token = read()
                                        if (token == "(") depth++
                                        else if (token == ")") depth--
                                    }
                                    listOf() // Return empty parameter list
                                }
                            }
                            expect(")")
                            // Create a function declarator with the function pointer as base
                            return@tag ParamDeclaratorPostfix(params).toDeclarator(funcPtrDecl)
                        }
                        // If not followed by parameter list, return the function pointer declarator
                        return@tag funcPtrDecl
                    } else {
                        // Rewind and try normal declarator
                        pos -= 1 // Go back to "*"
                    }
                }

                // Handle function declarations with complex parameter lists
                val startPos = pos
                try {
                    val decl = declarator()
                    expect(")")
                    return@tag decl
                } catch (e: ExpectException) {
                    // If we fail to parse as a declarator, rewind and try to handle it as a special case
                    pos = startPos

                    // Check if this might be a function pointer type declaration
                    // like (unsigned (*in_func)(void *, unsigned, unsigned), ...)
                    if (peek() == "unsigned" || peek() == "void" || peek() == "int" || peek() == "char" || peek() == "long" || peek() == "const") {
                        // Skip the return type and any qualifiers
                        while (peek() == "unsigned" || peek() == "void" || peek() == "int" || peek() == "char" || peek() == "long" || peek() == "const") {
                            read()
                        }

                        // Check for function pointer syntax
                        if (peek() == "(" && peekOutside(+1) == "*") {
                            expect("(")
                            expect("*")

                            // Read the function name if present
                            val funcName = if (Id.isValid(peek())) {
                                read()
                            } else {
                                "anonymous_func"
                            }

                            expect(")")

                            // Skip the parameter list
                            if (peek() == "(") {
                                expect("(")
                                var parenCount = 1
                                while (!eof && parenCount > 0) {
                                    val token = read()
                                    if (token == "(") parenCount++
                                    else if (token == ")") parenCount--
                                }
                            }

                            return@tag IdentifierDeclarator(IdDecl(funcName))
                        }
                    }

                    // If not a function pointer type, skip to the matching closing parenthesis
                    pos = startPos
                    var depth = 1
                    while (depth > 0 && !eof) {
                        val token = read()
                        if (token == "(") depth++
                        else if (token == ")") depth--
                    }
                    // Create a dummy declarator
                    return@tag IdentifierDeclarator(IdDecl(generateDummyFuncName()))
                }
            }
            else -> {
                if (Id.isValid(peek())) {
                    IdentifierDeclarator(identifierDecl())
                }
                // Not part of the declarator
                else {
                    null
                }
            }
        }
    } ?: return@tag null

    val postfixs = arrayListOf<DeclaratorPostfix>()
    loop@ while (true) {
        postfixs += tag {
            when (peek()) {
                // direct-declarator ( parameter-type-list )
                // direct-declarator ( identifier-listopt )
                "(" -> {
                    expect("(")
                    val params = if (peekOutside() == "void" && peekOutside(+1) == ")") {
                        expect("void")
                        listOf()
                    } else {
                        list(")", ",") { parameterDeclaration() }
                    }
                    expect(")")
                    ParamDeclaratorPostfix(params)
                }
                // direct-declarator [ type-qualifier-listopt assignment-expressionopt ]
                // direct-declarator [ static type-qualifier-listopt assignment-expression ]
                // direct-declarator [type-qualifier-list static assignment-expression]
                // direct-declarator [type-qualifier-listopt *]
                "[" -> {
                    expect("[")
                    val static0 = tryExpect("static") != null
                    val typeQualifiers = whileNotNull { tryTypeQualifier() }
                    val static1 = tryExpect("static") != null
                    val expr = tryExpression()
                    expect("]")
                    ArrayDeclaratorPostfix(typeQualifiers, expr, static0, static1)
                }
                else -> {
                    null
                }
            }
        } ?: break
    }

    var out = base
    for (postfix in postfixs.reversed()) out = postfix.toDeclarator(out)
    //for (postfix in postfixs) out = postfix.toDeclarator(out)
    return if (pointer != null) DeclaratorWithPointer(pointer, out) else out
}

// (6.7.9) designator:
fun ProgramParser.tryDesignator(): Designator? = tag {
    when (peek()) {
        DOT -> {
            expect(DOT)
            FieldAccessDesignator(identifier())
        }
        "[" -> {
            expect("[")
            val expr = constantExpression()
            expect("]")
            ArrayAccessDesignator(expr)
        }
        else -> null
    }
}

fun ProgramParser.designatorList(): List<Designator> = whileNotNull { tryDesignator() }

// (6.7.9) designation:
fun ProgramParser.tryDesignation(): DesignatorList? = tag {
    val design = designatorList()
    if (design.isNotEmpty()) {
        expect("=")
        DesignatorList(design)
    } else {
        null
    }
}

// (6.7.9) initializer:
fun ProgramParser.initializer(ltype: Type): Expr = tag {
    if (peek() == "{") {
        val elementType = ltype.elementType
        expect("{")
        val items = list("}", ",", tailingSeparator = true) {
            val designationOpt = tryDesignation()
            val initializer = initializer(elementType)
            DesignOptInit(designationOpt, initializer)
        }
        //val items = list("}", ",", tailingSeparator = true) { designOptInitializer(ltype.elementType) }
        expect("}")
        ArrayInitExpr(items, ltype)
    } else {
        tryAssignmentExpr() ?: error("Not an assignment-expression")
    }
}

// (6.7) init-declarator:
fun ProgramParser.initDeclarator(specsType: Type): InitDeclarator = tag {
    val decl = declarator()
    val ftype = specsType.withDeclarator(decl)
    val initializer = if (tryExpect("=") != null) initializer(ftype) else null
    if (initializer != null) {

        if (!initializer.type.canAssignTo(ftype, this)) {
            reportWarning("Can't assign ${initializer.type} to $ftype (${initializer.type.resolve(parser)} != ${ftype.resolve(parser)})")
        }
    }
    InitDeclarator(decl, initializer, ftype)
}

fun ProgramParser.staticAssert(): Nothing {
    expect("_Static_assert", "(")
    val expr = constantExpression()
    expect(",")
    val str = stringLiteral()
    expect(")")
    TODO("_Static_assert")
}

// (6.7) declaration:
fun ProgramParser.tryDeclaration(sure: Boolean = false): Decl? = tag {
    when (peek()) {
        "_Static_assert" -> staticAssert()
        else -> {
            // Try to handle special cases like function pointer typedefs
            val startPos = pos
            try {
                // Skip known macros that might appear before function declarations
                val skippedTokens = arrayListOf<String>()
                while (!eof) {
                    val token = peek()
                    // Skip compiler/library-specific macros and type qualifiers
                    if (token.startsWith("Z") || token.endsWith("INTERNAL") || 
                        token == "FAR" || token.endsWith("_const") || token == "local" || 
                        token == "extern" || token == "static") {
                        skippedTokens.add(read())
                    } else {
                        break
                    }
                }

                // General case for function pointer typedefs
                if (!eof && peek() == "typedef" && 
                    (peekOutside(+1) == "unsigned" || peekOutside(+1) == "int" || peekOutside(+1) == "void") && 
                    peekOutside(+2) == "(" && peekOutside(+3) == "*" && 
                    Id.isValid(peekOutside(+4)) && peekOutside(+4).endsWith("_func")) {
                    // Skip the typedef
                    read()
                    // Skip the return type
                    read()
                    // Skip the ( * identifier ) part
                    expect("(")
                    expect("*")
                    val funcName = read()
                    expect(")")
                    // Skip the parameter list
                    expect("(")
                    var parenCount = 1
                    while (!eof && parenCount > 0) {
                        val token = read()
                        if (token == "(") parenCount++
                        else if (token == ")") parenCount--
                    }
                    // Skip the semicolon
                    expect(";")

                    // Create a dummy typedef
                    val typedefSpec = StorageClassSpecifier(StorageClassSpecifier.Kind.TYPEDEF)
                    val intSpec = BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT)
                    val specs = ListTypeSpecifier(listOf(typedefSpec, intSpec))
                    val initDeclaratorList = listOf(InitDeclarator(IdentifierDeclarator(IdDecl(funcName)), null, Type.INT))
                    typedefTypes[funcName] = specs
                    typedefAliases[funcName] = Type.INT
                    return@tag VarDeclaration(specs, initDeclaratorList)
                }

                val specs = declarationSpecifiers(sure) ?: return@tag null
                if (specs.isEmpty()) return@tag null
                val specsType = specs.toFinalType()

                // Try to parse the init declarator list
                val initDeclaratorList = try {
                    list(";", ",") { initDeclarator(specsType) }
                } catch (e: ExpectException) {
                    // If we fail to parse the init declarator list, try to recover

                    // First, check if this might be a va_list declaration
                    if (specsType == Type.VA_LIST && peek() != ";") {
                        // Skip to the next semicolon
                        while (!eof && peek() != ";") {
                            read()
                        }
                        expect(";")

                        // Create a dummy va_list init declarator
                        listOf(InitDeclarator(IdentifierDeclarator(IdDecl("va")), null, Type.VA_LIST))
                    } else {
                        // Check if this might be a function pointer typedef
                        pos = startPos

                        // Skip to the next semicolon, but keep track of parentheses to handle nested structures
                        val tokens = arrayListOf<String>()
                        var parenCount = 0
                        var foundSemicolon = false

                        while (!eof) {
                            val token = peek()
                            if (token == "(") parenCount++
                            else if (token == ")") parenCount--
                            else if (token == ";" && parenCount == 0) {
                                foundSemicolon = true
                                break
                            }
                            tokens.add(read())
                        }

                        if (foundSemicolon) {
                            expect(";")
                        }

                        // Look for function pointer patterns in the tokens
                        var funcPtrName = generateDummyFuncName()
                        for (i in 0 until tokens.size - 2) {
                            if (tokens[i] == "(" && tokens[i+1] == "*" && Id.isValid(tokens[i+2])) {
                                funcPtrName = tokens[i+2]
                                break
                            }
                        }

                        // Create a dummy init declarator for the function pointer
                        listOf(InitDeclarator(IdentifierDeclarator(IdDecl(funcPtrName)), null, specsType))
                    }
                }

                if (peek() == ";") {
                    expect(";")
                }

                for (item in initDeclaratorList) {
                    val nameId = item.declarator.getNameId()
                    val token = token(nameId.pos)
                    val name = nameId.getName()
                    val itemType = specs.toFinalType(item.declarator)

                    if (specs.hasTypedef && !typedefTypes.containsKey(name)) {
                        // @TODO: Merge those?
                        typedefTypes[name] = specs
                        typedefAliases[name] = itemType

                        val structTypeSpecifier = specs.items.filterIsInstance<StructUnionTypeSpecifier>().firstOrNull()
                        if (structTypeSpecifier != null) {
                            val structType = getStructTypeInfo(structTypeSpecifier)
                            structTypesByName.remove(structType.name)
                            structType.name = name
                            structTypesByName[structType.name] = structType
                        }

                        //out.firstIsInstance<TypedefTypeSpecifier>().id
                        //println("hasTypedef: $hasTypedef")
                    } else {
                        symbols.registerInfo(nameId.id.name, itemType, nameId, token)
                    }
                }
                VarDeclaration(specs, initDeclaratorList)
            } catch (e: Exception) {
                // If we fail to parse the declaration, try to recover
                // Report a warning instead of an error
                reportWarning("Error parsing declaration: ${e.message}")

                // Skip to the next semicolon
                pos = startPos

                // Try to handle function pointer typedefs and function declarations with macros
                val tokens = arrayListOf<String>()
                var parenCount = 0
                var foundSemicolon = false

                while (!eof) {
                    val token = peek()
                    if (token == "(") parenCount++
                    else if (token == ")") parenCount--
                    else if (token == ";" && parenCount == 0) {
                        foundSemicolon = true
                        break
                    }
                    tokens.add(read())
                }

                if (foundSemicolon) {
                    expect(";")
                }

                // Look for function pointer patterns in the tokens
                var funcPtrName = generateDummyFuncName()
                for (i in 0 until tokens.size - 2) {
                    if (tokens[i] == "(" && tokens[i+1] == "*" && Id.isValid(tokens[i+2])) {
                        funcPtrName = tokens[i+2]
                        break
                    }
                }

                // Create a dummy declaration
                val specs = ListTypeSpecifier(listOf(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT)))
                val initDeclaratorList = listOf(InitDeclarator(IdentifierDeclarator(IdDecl(funcPtrName)), null, Type.INT))
                VarDeclaration(specs, initDeclaratorList)
            }
        }
    }
}

fun Declaration(type: Type, name: String, init: Expr? = null): VarDeclaration {
    return VarDeclaration(
        ListTypeSpecifier(listOf(BasicTypeSpecifier(BasicTypeSpecifier.Kind.INT))), listOf(
            InitDeclarator(IdentifierDeclarator(IdDecl(name)), init, type)
        )
    )
}

fun ProgramParser.declaration(sure: Boolean = true): Decl = tryDeclaration(sure = sure)
    ?: parserException("TODO: ProgramParser.declaration")

fun ProgramParser.recovery(tokens: Set<String>) {
    if (eof) {
        error("EOF")
    }

    val spos = pos
    while (!eof && peek() !in tokens) read()
    val epos = pos

    // We have to skip something to recover, or we will end in an infinite loop
    if (!eof && spos == epos) {
        read()
    }
}

private val compoundStatementRecoveryTokens = setOf(
    ";", "}", "if", "return", "switch", "while", "do", "for", "goto", "continue", "break"
)

// (6.8.2) compound-statement:
fun ProgramParser.compoundStatement(): Stms = tag {
    scopeSymbols {
        expect("{")
        val stms = arrayListOf<Stm>()
        while (!eof && peekOutside() != "}") {
            val spos = this.pos
            try {
                stms += blockItem()
            } catch (e: ParserException) {
                pos = spos
                reportError(e)
                recovery(compoundStatementRecoveryTokens)
                if (peekOutside() == ";") expect(";")
            }
        }
        expect("}")
        Stms(stms)
    }
}

fun ParameterDecl.toCParam(): CParam = CParam(
    this,
    this.specs.toFinalType().withDeclarator(declarator),
    this.declarator.getNameId()
)

fun Declarator.extractParameter(): ParameterDeclarator = when {
    this is DeclaratorWithPointer -> this.declarator.extractParameter()
    this is ParameterDeclarator -> this
    this is IdentifierDeclarator -> ParameterDeclarator(this, listOf()) // @TODO: Check this!
    else -> error("Not a DeclaratorWithPointer $this")
}

// (6.9.1) function-definition:
fun ProgramParser.functionDefinition(): FuncDeclaration = tag {
    // Skip known macros that might appear before function declarations
    val skippedTokens = arrayListOf<String>()
    while (!eof) {
        val token = peek()
        // Skip compiler/library-specific macros, type qualifiers, and storage class specifiers
        // Using a general approach that works for any library
        if (token.startsWith("__") || token.endsWith("__") || token.endsWith("INTERNAL") || 
            token == "FAR" || token.endsWith("_const") || token == "local" || 
            token == "extern" || token == "static" || token.contains("EXPORT") || token.contains("EXTERN") ||
            token.startsWith("_") && token.length > 1 && token[1].isUpperCase() || // Common pattern for library macros
            token.length >= 2 && token[0].isUpperCase() && token[1].isUpperCase()) { // All-caps macros
            skippedTokens.add(read())
        } else {
            break
        }
    }

    val rettype = declarationSpecifiers() ?: parserException("Can't declarationSpecifiers $this")

    val decl = try {
        declarator()
    } catch (e: ExpectException) {
        // Try to recover from errors in declarator parsing
        // This is common with complex function declarations
        val startPos = pos
        var parenCount = 0
        while (!eof) {
            val token = peek()
            if (token == "(") parenCount++
            else if (token == ")") {
                parenCount--
                if (parenCount == 0) break
            }
            read()
        }
        if (!eof) read() // Consume the closing parenthesis

        // Create a dummy declarator
        IdentifierDeclarator(IdDecl(generateDummyFuncName()))
    }

    val paramDecl = try {
        decl.extractParameter()
    } catch (e: Throwable) {
        // Create a dummy parameter declarator
        ParameterDeclarator(IdentifierDeclarator(IdDecl(generateDummyFuncName())), listOf())
    }

    if (paramDecl.base !is IdentifierDeclarator) {
        // Create a dummy function declaration
        val dummyName = generateDummyFuncName()
        val name = IdDecl(dummyName)
        val params = listOf<CParam>()
        val funcType = FunctionType(dummyName, Type.INT, listOf(), false)
        return@tag FuncDeclaration(rettype, name, params, Stms(listOf()), false, funcType)
    }

    val name = paramDecl.base.id
    val variadic = paramDecl.decls.any { it.declarator is VarargDeclarator }
    val params = paramDecl.decls.filter { it.declarator !is VarargDeclarator }.map { it.toCParam() }

    val funcType = try {
        val type = rettype.toFinalType(decl)
        if (type !is FunctionType) {
            FunctionType(name.name, Type.INT, params.map { it.toFParam() }, variadic)
        } else {
            type
        }
    } catch (e: Throwable) {
        // Create a dummy function type
        FunctionType(name.name, Type.INT, params.map { it.toFParam() }, variadic)
    }
    symbols.registerInfo(name.name, funcType, name, token(name))
    scopeFunction {
        _functionScope?.apply {
            this.name = name.name
            this.type = funcType
        }
        scopeSymbols {
            for (param in params) {
                symbols.registerInfo(param.name.name, param.type, param.nameId, token(param.nameId.pos))
            }
            val body = compoundStatement()
            FuncDeclaration(rettype, name, params, body, variadic, funcType).apply {
                func = _functionScope
            }
        }
    }
}

// (6.9) external-declaration
fun ProgramParser.tryExternalDeclaration(): Decl? = tag {
    try {
        consumeLineMarkers()
        if (!eof) {
            tryBlocks(
                "external-declaration", this,
                {
                    consumeLineMarkers()
                    declaration(sure = false)
                },
                {
                    consumeLineMarkers()
                    functionDefinition()
                },
                propagateLast = true
            )
        } else {
            null
        }
    } catch (e: Throwable) {
        reportError(e)
        skip(1)
        null
    }
}

// (6.7.2) type-specifier:
fun ProgramParser.tryTypeSpecifier(): TypeSpecifier? = tag<TypeSpecifier> {
    TODO("tryTypeSpecifier")
    //when (peek()) { }
}

fun ProgramParser.typeSpecifier() = tryTypeSpecifier() ?: error("Not a type specifier at '${this.peek()}'")

// (6.9) translation-unit
fun ProgramParser.translationUnits() = tag {
    //println("PROGRAM: ${this@translationUnits.tokens}")
    val decls = arrayListOf<Decl>()
    try {
        while (true) {
            consumeLineMarkers()
            if (eof) break
            val externalDefinition = tryExternalDeclaration() ?: continue
            //println("externalDefinition: $externalDefinition")
            decls += externalDefinition
        }
    } catch (eof: EOFException) {

    }
    Program(decls)
}

fun ProgramParser.program(): Program = translationUnits()
fun ProgramParser.parsedProgram(info: PreprocessorInfo): ParsedProgram = ParsedProgram(program(), this, info)

fun ListReader<CToken>.programParser() = ProgramParser(this.items.map { it.str }, this.items, this.pos)
fun ListReader<String>.program() = ListReader(this.items.map { CToken(it) }, CToken("")).programParser().program()
fun String.programParser() = tokenize().programParser()

operator fun Number.times(other: Number): Number {
    if (this is Int && other is Int) return this * other
    TODO("Number.times $this (${this::class}), $other (${other::class})")
}

operator fun Number.plus(other: Number): Number {
    if (this is Int && other is Int) return this + other
    TODO("Number.times $this (${this::class}), $other (${other::class})")
}

open class EvalContext {
    open fun resolveId(id: String): Any? {
        error("Unknown identifier '$id'")
    }

    open fun callFunction(id: String, args: Array<Any?>): Any? {
        error("Unknown function $id")
    }
}

fun Any?.toBool(): Boolean = when (this) {
    null -> false
    is Boolean -> this
    is Number -> toInt() != 0
    is String -> isNotBlank() && this != "0" && this != "false"
    else -> false
}

fun Any?.toNumber(): Number = when (this) {
    is Boolean -> if (this) 1 else 0
    is Number -> this
    is String -> this.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

fun Any?.toDouble(): Double = toNumber().toDouble()
fun Any?.toInt(): Int = toNumber().toInt()

fun Expr.constantEvaluate(ctx: EvalContext = EvalContext()): Any? = when (this) {
    is Binop -> {
        val lv = this.l.constantEvaluate(ctx)
        val rv = this.r.constantEvaluate(ctx)
        when (op) {
            "+" -> (lv.toInt()) + (rv.toInt())
            "-" -> (lv.toInt()) - (rv.toInt())
            "*" -> (lv.toInt()) * (rv.toInt())
            "/" -> (lv.toInt()) / (rv.toInt())
            "%" -> (lv.toInt()) % (rv.toInt())
            "<" -> (lv.toDouble()) < (rv.toDouble())
            ">" -> (lv.toDouble()) > (rv.toDouble())
            "<=" -> (lv.toDouble()) <= (rv.toDouble())
            ">=" -> (lv.toDouble()) >= (rv.toDouble())
            "==" -> (lv.toDouble()) == (rv.toDouble())
            "!=" -> (lv.toDouble()) != (rv.toDouble())
            "&&" -> lv.toBool() && rv.toBool()
            "||" -> lv.toBool() || rv.toBool()
            else -> TODO("Binop: $op")
        }
    }
    is Unop -> {
        val rv = this.rvalue.constantEvaluate(ctx)
        when (op) {
            "!" -> !rv.toBool()
            "-" -> -(rv.toNumber().toInt())
            "+" -> +(rv.toNumber().toInt())
            else -> TODO("Unop: $op")
        }
    }
    is IntConstant -> this.value
    is DecimalConstant -> this.value
    is StringConstant -> this.value
    is CharConstant -> this.value
    is Id -> ctx.resolveId(name)
    is CallExpr -> {
        if (this.expr !is Id) error("Can't evaluate function $expr")
        ctx.callFunction(expr.name, this.args.map { it.constantEvaluate(ctx) }.toTypedArray())
    }
    else -> error("Don't know how to constant-evaluate ${this::class} '$this'")
}

// Annex A: (6.4.1)
val keywords = setOf(
    "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern",
    "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return", "short", "signed",
    "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
    "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex", "_Generic", "_Imaginary", "_Noreturn", "_Static_assert", "_Thread_local"
)

// (6.7.1) storage-class-specifier:
val storageClassSpecifiers = setOf("typedef", "extern", "static", "_Thread_local", "auto", "register")

// (6.7.2) type-specifier:
val typeSpecifier = setOf("void", "char", "short", "int", "long", "float", "double", "signed", "unsigned", "_Bool", "_Complex")

val unaryOperators = setOf("&", "*", "+", "-", "~", "!")
val assignmentOperators = setOf("=", "*=", "/=", "%=", "+=", "-=", "<<=", ">>=", "&=", "^=", "|=")
val binaryOperators = setOf(
    "*", "/", "%",
    "+", "-",
    "<<", ">>",
    "<", ">", "<=", ">=",
    "==", "!=",
    "&", "^", "|",
    "&&", "||"
)
val ternaryOperators = setOf("?", ":")
val postPreFixOperators = setOf("++", "--")

val allOperators = unaryOperators + binaryOperators + ternaryOperators + postPreFixOperators + assignmentOperators
