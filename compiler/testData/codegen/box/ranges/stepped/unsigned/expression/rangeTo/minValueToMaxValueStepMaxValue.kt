// Auto-generated by GenerateSteppedRangesCodegenTestData. Do not edit!
// DONT_TARGET_EXACT_BACKEND: WASM
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME
import kotlin.test.*

fun box(): String {
    val uintList = mutableListOf<UInt>()
    val uintProgression = UInt.MIN_VALUE..UInt.MAX_VALUE
    for (i in uintProgression step Int.MAX_VALUE) {
        uintList += i
    }
    assertEquals(listOf(UInt.MIN_VALUE, 2147483647u, UInt.MAX_VALUE - 1u), uintList)

    val ulongList = mutableListOf<ULong>()
    val ulongProgression = ULong.MIN_VALUE..ULong.MAX_VALUE
    for (i in ulongProgression step Long.MAX_VALUE) {
        ulongList += i
    }
    assertEquals(listOf(ULong.MIN_VALUE, 9223372036854775807uL, ULong.MAX_VALUE - 1uL), ulongList)

    return "OK"
}