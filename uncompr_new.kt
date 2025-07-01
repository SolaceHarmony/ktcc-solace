// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='*', pos=1391, token=CToken(str=*, pos=96236, row=2556, lineStart=96228), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=*, pos=96236, row=2556, lineStart=96228), pos=1391, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1396, token=CToken(str=}, pos=96254, row=2557, lineStart=96250), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=96254, row=2557, lineStart=96250), pos=1396, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1407, token=CToken(str=}, pos=96309, row=2561, lineStart=96305), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=96309, row=2561, lineStart=96305), pos=1407, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1527, token=CToken(str=}, pos=96790, row=2579, lineStart=96782), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=96790, row=2579, lineStart=96782), pos=1527, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1561, token=CToken(str=}, pos=96938, row=2583, lineStart=96930), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=96938, row=2583, lineStart=96930), pos=1561, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1572, token=CToken(str=}, pos=96979, row=2585, lineStart=96975), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=96979, row=2585, lineStart=96975), pos=1572, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='*', pos=1580, token=CToken(str=*, pos=97004, row=2587, lineStart=97000), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=*, pos=97004, row=2587, lineStart=97000), pos=1580, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1662, token=CToken(str=}, pos=97320, row=2598, lineStart=97320), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=97320, row=2598, lineStart=97320), pos=1662, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// ERROR: ProgramMessage(message=Can't declarationSpecifiers ProgramParser(current='}', pos=1696, token=CToken(str=}, pos=97491, row=2603, lineStart=97491), marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10)), token=CToken(str=}, pos=97491, row=2603, lineStart=97491), pos=1696, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=ERROR)
// WARNING: ProgramMessage(message=Can't find identifier 'stream'. Asumed as int., token=CToken(str=stream, pos=97081, row=2589, lineStart=97062), pos=1598, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=WARNING)
// WARNING: ProgramMessage(message=Can't find identifier 'total_out'. Asumed as int., token=CToken(str=;, pos=97097, row=2589, lineStart=97062), pos=1601, marker=Marker(originalPos=1324, originalRow1=2528, translatedFile=zlib-1.3.1/uncompr.c, translatedRow1=10), level=WARNING)
//ENTRY Program
//Program.main(arrayOf())
@Suppress("MemberVisibilityCanBePrivate", "FunctionName", "CanBeVal", "DoubleNegation", "LocalVariableName", "NAME_SHADOWING", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "RemoveRedundantCallsOfConversionMethods", "EXPERIMENTAL_IS_NOT_ENABLED", "RedundantExplicitType", "RemoveExplicitTypeArguments", "RedundantExplicitType", "unused", "UNCHECKED_CAST", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "NOTHING_TO_INLINE", "PropertyName", "ClassName", "USELESS_CAST", "PrivatePropertyName", "CanBeParameter", "UnusedMainParameter")
@OptIn(ExperimentalUnsignedTypes::class)
public open class Program(HEAP_SIZE: Int = 0) : Runtime(HEAP_SIZE) {
	companion object {
		const val MAX_MEM_LEVEL = 9
		const val MAX_WBITS = 15
		const val SEEK_SET = 0
		const val SEEK_CUR = 1
		const val SEEK_END = 2
		const val ZLIB_VERSION = 0
		const val ZLIB_VERNUM = 4880
		const val ZLIB_VER_MAJOR = 1
		const val ZLIB_VER_MINOR = 3
		const val ZLIB_VER_REVISION = 1
		const val ZLIB_VER_SUBREVISION = 0
		const val Z_NO_FLUSH = 0
		const val Z_PARTIAL_FLUSH = 1
		const val Z_SYNC_FLUSH = 2
		const val Z_FULL_FLUSH = 3
		const val Z_FINISH = 4
		const val Z_BLOCK = 5
		const val Z_TREES = 6
		const val Z_OK = 0
		const val Z_STREAM_END = 1
		const val Z_NEED_DICT = 2
		const val Z_ERRNO = -1
		const val Z_STREAM_ERROR = -2
		const val Z_DATA_ERROR = -3
		const val Z_MEM_ERROR = -4
		const val Z_BUF_ERROR = -5
		const val Z_VERSION_ERROR = -6
		const val Z_NO_COMPRESSION = 0
		const val Z_BEST_SPEED = 1
		const val Z_BEST_COMPRESSION = 9
		const val Z_DEFAULT_COMPRESSION = -1
		const val Z_FILTERED = 1
		const val Z_HUFFMAN_ONLY = 2
		const val Z_RLE = 3
		const val Z_FIXED = 4
		const val Z_DEFAULT_STRATEGY = 0
		const val Z_BINARY = 0
		const val Z_TEXT = 1
		const val Z_ASCII = 1
		const val Z_UNKNOWN = 2
		const val Z_DEFLATED = 8
		const val Z_NULL = 0
	}
	
	// typealias voidpc = CPointer<Unit>
	// typealias voidpf = CPointer<Unit>
	// typealias voidp = CPointer<Unit>
	// typealias alloc_func = CFunction<(Int, UInt, UInt) -> Int>
	// typealias free_func = Int
	// typealias z_streamp = CPointer<CPointer<Unit>>
	// typealias gz_headerp = IntPointer
	// var zlibVersion: CFunction<() -> CPointer<Byte>> = 0 /*CFunction<() -> CPointer<Byte>>*/
	var dummy_func_0: Int = 0
	var dummy_func_1: Int = 0
	var dummy_func_2: Int = 0
	var dummy_func_3: Int = 0
	var dummy_func_4: Int = 0
	var dummy_func_5: Int = 0
	var dummy_func_6: Int = 0
	var dummy_func_7: Int = 0
	var dummy_func_8: Int = 0
	var dummy_func_9: Int = 0
	var dummy_func_10: ULong = 0uL
	var dummy_func_11: Int = 0
	var dummy_func_12: Int = 0
	var dummy_func_13: Int = 0
	var dummy_func_14: Int = 0
	var dummy_func_15: Int = 0
	var dummy_func_16: Int = 0
	var dummy_func_17: Int = 0
	var dummy_func_18: Int = 0
	var dummy_func_19: Int = 0
	var dummy_func_20: Int = 0
	var dummy_func_21: Int = 0
	var dummy_func_22: Int = 0
	// typealias in_func = Int
	// typealias out_func = Int
	var dummy_func_23: Int = 0
	var dummy_func_24: Int = 0
	var void: ULong = 0uL
	var dummy_func_25: Int = 0
	var dummy_func_26: Int = 0
	var dummy_func_27: ULong = 0uL
	var dummy_func_28: Int = 0
	var dummy_func_29: Int = 0
	// typealias gzFile = CPointer<UNKNOWN>
	var dummy_func_30: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_31: Int = 0
	var dummy_func_32: Int = 0
	var dummy_func_33: Int = 0
	var dummy_func_34: Int = 0
	var dummy_func_35: Int = 0
	var dummy_func_36: Int = 0
	var dummy_func_37: Int = 0
	var dummy_func_38: Int = 0
	// var gzgets: CFunction<(CPointer<Unit>, CPointer<Byte>, Int) -> CPointer<Byte>> = 0 /*CFunction<(gzFile, CPointer<Byte>, len) -> CPointer<Byte>>*/
	var dummy_func_39: Int = 0
	var dummy_func_40: Int = 0
	var dummy_func_41: Int = 0
	var dummy_func_42: Int = 0
	var dummy_func_43: Int = 0
	var dummy_func_44: Int = 0
	var dummy_func_45: Int = 0
	var dummy_func_46: Int = 0
	var dummy_func_47: Int = 0
	var dummy_func_48: Int = 0
	// var gzerror: CFunction<(CPointer<Unit>, CPointer<Int>) -> CPointer<Byte>> = 0 /*CFunction<(gzFile, CPointer<Int>) -> CPointer<Byte>>*/
	var dummy_func_49: Int = 0
	var dummy_func_50: ULong = 0uL
	var dummy_func_51: ULong = 0uL
	var dummy_func_52: ULong = 0uL
	var dummy_func_53: ULong = 0uL
	var dummy_func_54: ULong = 0uL
	var dummy_func_55: Int = 0
	var dummy_func_56: Int = 0
	var dummy_func_57: Int = 0
	var dummy_func_58: Int = 0
	var dummy_func_59: Int = 0
	var dummy_func_60: Int = 0
	var dummy_func_61: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_62: Int = 0
	var gzFile: Int = 0
	var gzFile: Int = 0
	var dummy_func_63: ULong = 0uL
	var dummy_func_64: ULong = 0uL
	var long: ULong = 0uL
	// var zError: CFunction<(Int) -> CPointer<Byte>> = 0 /*CFunction<(Int) -> CPointer<Byte>>*/
	var z_streamp: Int = 0
	// var get_crc_table: CFunction<() -> CPointer<Int>> = 0 /*CFunction<() -> CPointer<z_crc_t>>*/
	var dummy_func_65: Int = 0
	var dummy_func_66: Int = 0
	var z_streamp: Int = 0
	var z_streamp: CPointer<Unit> = CPointer<Unit>(0)
	var z_streamp: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_67: Int = 0
	var dummy_func_69: Int = 0
	var dummy_func_70: UInt = 0u
	var dummy_func_71: ULong = 0uL
	var dummy_func_72: UByte = 0u
	var dummy_func_73: Int = 0
	var destLen: Int = 0
	var dummy_func_75: Int = 0
	var dummy_func_76: Int = 0
	var dummy_func_77: Int = 0
	var dummy_func_78: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_79: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_80: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_81: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_82: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_83: Int = 0
	var dummy_func_84: Int = 0
	var return: Int = 0
	var err: Int = 0
	var dummy_func_85: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_86: CPointer<Unit> = CPointer<Unit>(0)
	var dummy_func_87: Int = 0
	var dummy_func_88: Int = 0
	var dummy_func_90: Int = 0
	var dummy_func_91: Int = 0
	var dummy_func_92: Int = 0
	var dummy_func_93: Int = 0
	var dummy_func_94: Int = 0
	var dummy_func_95: Int = 0
	var destLen: IntPointer = IntPointer(stream.total_out)
	var dummy_func_96: Int = 0
	var left: Int = 1
	var dummy_func_97: Int = 0
	var dummy_func_98: Int = 0
	var dummy_func_100: Int = 0
	
	//////////////////
	// C STRUCTURES //
	//////////////////
	
	//////////////////
	fun z_stream_sAlloc(): z_stream_s = z_stream_s(alloca(z_stream_s__SIZE_BYTES).ptr)
	fun z_stream_sAlloc(next_in: CPointer<CPointer<UByte>>, next_out: CPointer<CPointer<UByte>>, msg: CPointer<Byte>, state: CPointer<UNKNOWN>): z_stream_s = z_stream_sAlloc().apply { this.next_in = next_in; this.next_out = next_out; this.msg = msg; this.state = state }
	fun z_stream_s.copyFrom(src: z_stream_s): z_stream_s = this.apply { memcpy(CPointer<Unit>(this.ptr), CPointer<Unit>(src.ptr), z_stream_s__SIZE_BYTES) }
	inline fun fixedArrayOfz_stream_s(size: Int, setItems: CPointer<z_stream_s>.() -> Unit): CPointer<z_stream_s> = CPointer<z_stream_s>(alloca_zero(size * z_stream_s__SIZE_BYTES).ptr).apply(setItems)
	@kotlin.jvm.JvmName("getz_stream_s") operator fun CPointer<z_stream_s>.get(index: Int): z_stream_s = z_stream_s(this.ptr + index * z_stream_s__SIZE_BYTES)
	operator fun CPointer<z_stream_s>.set(index: Int, value: z_stream_s) = z_stream_s(this.ptr + index * z_stream_s__SIZE_BYTES).copyFrom(value)
	@kotlin.jvm.JvmName("plusz_stream_s") operator fun CPointer<z_stream_s>.plus(offset: Int): CPointer<z_stream_s> = CPointer<z_stream_s>(this.ptr + offset * z_stream_s__SIZE_BYTES)
	@kotlin.jvm.JvmName("minusz_stream_s") operator fun CPointer<z_stream_s>.minus(offset: Int): CPointer<z_stream_s> = CPointer<z_stream_s>(this.ptr - offset * z_stream_s__SIZE_BYTES)
	fun CPointer<z_stream_s>.minusPtrz_stream_s(other: CPointer<z_stream_s>) = (this.ptr - other.ptr) / z_stream_s__SIZE_BYTES
	@get:kotlin.jvm.JvmName("getz_stream_s") var CPointer<z_stream_s>.value: z_stream_s get() = this[0]; set(value) { this[0] = value }
	/// z_stream_s fields {
	  var z_stream_s.next_in: CPointer<CPointer<UByte>> get() = CPointer<CPointer<UByte>>(lw(ptr + z_stream_s__OFFSET_next_in)); set(value) { sw(ptr + z_stream_s__OFFSET_next_in, value.ptr) }
	  var z_stream_s.next_out: CPointer<CPointer<UByte>> get() = CPointer<CPointer<UByte>>(lw(ptr + z_stream_s__OFFSET_next_out)); set(value) { sw(ptr + z_stream_s__OFFSET_next_out, value.ptr) }
	  var z_stream_s.msg: CPointer<Byte> get() = CPointer<Byte>(lw(ptr + z_stream_s__OFFSET_msg)); set(value) { sw(ptr + z_stream_s__OFFSET_msg, value.ptr) }
	  var z_stream_s.state: CPointer<UNKNOWN> get() = CPointer<UNKNOWN>(lw(ptr + z_stream_s__OFFSET_state)); set(value) { sw(ptr + z_stream_s__OFFSET_state, value.ptr) }
	/// }
	
	//////////////////
	fun gz_header_sAlloc(): gz_header_s = gz_header_s(alloca(gz_header_s__SIZE_BYTES).ptr)
	fun gz_header_sAlloc(extra: CPointer<CPointer<UByte>>, name: CPointer<CPointer<UByte>>, comment: CPointer<CPointer<UByte>>): gz_header_s = gz_header_sAlloc().apply { this.extra = extra; this.name = name; this.comment = comment }
	fun gz_header_s.copyFrom(src: gz_header_s): gz_header_s = this.apply { memcpy(CPointer<Unit>(this.ptr), CPointer<Unit>(src.ptr), gz_header_s__SIZE_BYTES) }
	inline fun fixedArrayOfgz_header_s(size: Int, setItems: CPointer<gz_header_s>.() -> Unit): CPointer<gz_header_s> = CPointer<gz_header_s>(alloca_zero(size * gz_header_s__SIZE_BYTES).ptr).apply(setItems)
	@kotlin.jvm.JvmName("getgz_header_s") operator fun CPointer<gz_header_s>.get(index: Int): gz_header_s = gz_header_s(this.ptr + index * gz_header_s__SIZE_BYTES)
	operator fun CPointer<gz_header_s>.set(index: Int, value: gz_header_s) = gz_header_s(this.ptr + index * gz_header_s__SIZE_BYTES).copyFrom(value)
	@kotlin.jvm.JvmName("plusgz_header_s") operator fun CPointer<gz_header_s>.plus(offset: Int): CPointer<gz_header_s> = CPointer<gz_header_s>(this.ptr + offset * gz_header_s__SIZE_BYTES)
	@kotlin.jvm.JvmName("minusgz_header_s") operator fun CPointer<gz_header_s>.minus(offset: Int): CPointer<gz_header_s> = CPointer<gz_header_s>(this.ptr - offset * gz_header_s__SIZE_BYTES)
	fun CPointer<gz_header_s>.minusPtrgz_header_s(other: CPointer<gz_header_s>) = (this.ptr - other.ptr) / gz_header_s__SIZE_BYTES
	@get:kotlin.jvm.JvmName("getgz_header_s") var CPointer<gz_header_s>.value: gz_header_s get() = this[0]; set(value) { this[0] = value }
	/// gz_header_s fields {
	  var gz_header_s.extra: CPointer<CPointer<UByte>> get() = CPointer<CPointer<UByte>>(lw(ptr + gz_header_s__OFFSET_extra)); set(value) { sw(ptr + gz_header_s__OFFSET_extra, value.ptr) }
	  var gz_header_s.name: CPointer<CPointer<UByte>> get() = CPointer<CPointer<UByte>>(lw(ptr + gz_header_s__OFFSET_name)); set(value) { sw(ptr + gz_header_s__OFFSET_name, value.ptr) }
	  var gz_header_s.comment: CPointer<CPointer<UByte>> get() = CPointer<CPointer<UByte>>(lw(ptr + gz_header_s__OFFSET_comment)); set(value) { sw(ptr + gz_header_s__OFFSET_comment, value.ptr) }
	/// }
	
	//////////////////
	fun gzFileAlloc(): gzFile = gzFileAlloc().apply {  }
	fun gzFile.copyFrom(src: gzFile): gzFile = this.apply { memcpy(CPointer<Unit>(this.ptr), CPointer<Unit>(src.ptr), UNKNOWN__SIZE_BYTES) }
	inline fun fixedArrayOfgzFile(size: Int, setItems: CPointer<UNKNOWN>.() -> Unit): CPointer<UNKNOWN> = CPointer<UNKNOWN>(alloca_zero(size * UNKNOWN__SIZE_BYTES).ptr).apply(setItems)
	@kotlin.jvm.JvmName("getgzFile") operator fun CPointer<UNKNOWN>.get(index: Int): gzFile = gzFile(this.ptr + index * UNKNOWN__SIZE_BYTES)
	operator fun CPointer<UNKNOWN>.set(index: Int, value: gzFile) = gzFile(this.ptr + index * UNKNOWN__SIZE_BYTES).copyFrom(value)
	@kotlin.jvm.JvmName("plusgzFile") operator fun CPointer<UNKNOWN>.plus(offset: Int): CPointer<UNKNOWN> = CPointer<UNKNOWN>(this.ptr + offset * UNKNOWN__SIZE_BYTES)
	@kotlin.jvm.JvmName("minusgzFile") operator fun CPointer<UNKNOWN>.minus(offset: Int): CPointer<UNKNOWN> = CPointer<UNKNOWN>(this.ptr - offset * UNKNOWN__SIZE_BYTES)
	fun CPointer<UNKNOWN>.minusPtrgzFile(other: CPointer<UNKNOWN>) = (this.ptr - other.ptr) / UNKNOWN__SIZE_BYTES
	@get:kotlin.jvm.JvmName("getgzFile") var CPointer<UNKNOWN>.value: gzFile get() = this[0]; set(value) { this[0] = value }
	/// gzFile fields {
	/// }
	
	//////////////////
	fun gzFile_sAlloc(): gzFile_s = gzFile_s(alloca(gzFile_s__SIZE_BYTES).ptr)
	fun gzFile_sAlloc(next: CPointer<UByte>): gzFile_s = gzFile_sAlloc().apply { this.next = next }
	fun gzFile_s.copyFrom(src: gzFile_s): gzFile_s = this.apply { memcpy(CPointer<Unit>(this.ptr), CPointer<Unit>(src.ptr), gzFile_s__SIZE_BYTES) }
	inline fun fixedArrayOfgzFile_s(size: Int, setItems: CPointer<gzFile_s>.() -> Unit): CPointer<gzFile_s> = CPointer<gzFile_s>(alloca_zero(size * gzFile_s__SIZE_BYTES).ptr).apply(setItems)
	@kotlin.jvm.JvmName("getgzFile_s") operator fun CPointer<gzFile_s>.get(index: Int): gzFile_s = gzFile_s(this.ptr + index * gzFile_s__SIZE_BYTES)
	operator fun CPointer<gzFile_s>.set(index: Int, value: gzFile_s) = gzFile_s(this.ptr + index * gzFile_s__SIZE_BYTES).copyFrom(value)
	@kotlin.jvm.JvmName("plusgzFile_s") operator fun CPointer<gzFile_s>.plus(offset: Int): CPointer<gzFile_s> = CPointer<gzFile_s>(this.ptr + offset * gzFile_s__SIZE_BYTES)
	@kotlin.jvm.JvmName("minusgzFile_s") operator fun CPointer<gzFile_s>.minus(offset: Int): CPointer<gzFile_s> = CPointer<gzFile_s>(this.ptr - offset * gzFile_s__SIZE_BYTES)
	fun CPointer<gzFile_s>.minusPtrgzFile_s(other: CPointer<gzFile_s>) = (this.ptr - other.ptr) / gzFile_s__SIZE_BYTES
	@get:kotlin.jvm.JvmName("getgzFile_s") var CPointer<gzFile_s>.value: gzFile_s get() = this[0]; set(value) { this[0] = value }
	/// gzFile_s fields {
	  var gzFile_s.next: CPointer<UByte> get() = CPointer<UByte>(lw(ptr + gzFile_s__OFFSET_next)); set(value) { sw(ptr + gzFile_s__OFFSET_next, value.ptr) }
	/// }
	
}

//////////////////
// C STRUCTURES //
//////////////////

//////////////////
public @kotlin.jvm.JvmInline value/*!*/ class z_stream_s(val ptr: Int)
const val z_stream_s__SIZE_BYTES = 16
const val z_stream_s__OFFSET_next_in = 0
const val z_stream_s__OFFSET_next_out = 4
const val z_stream_s__OFFSET_msg = 8
const val z_stream_s__OFFSET_state = 12
//////////////////
public @kotlin.jvm.JvmInline value/*!*/ class gz_header_s(val ptr: Int)
const val gz_header_s__SIZE_BYTES = 12
const val gz_header_s__OFFSET_extra = 0
const val gz_header_s__OFFSET_name = 4
const val gz_header_s__OFFSET_comment = 8
//////////////////
public @kotlin.jvm.JvmInline value/*!*/ class gzFile(val ptr: Int)
const val UNKNOWN__SIZE_BYTES = 0
//////////////////
public @kotlin.jvm.JvmInline value/*!*/ class gzFile_s(val ptr: Int)
const val gzFile_s__SIZE_BYTES = 4
const val gzFile_s__OFFSET_next = 0
