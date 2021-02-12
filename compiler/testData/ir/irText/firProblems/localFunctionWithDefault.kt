// !LANGUAGE: +FunctionReferenceWithDefaultValueAsOtherType

fun call1(f: (String, String) -> String, x: String, y: String): String = f(x, y)

fun box(): String {

    var s = "1"

    fun foo(x: String, y: String = "5", z: String = "4"): String = s + x + y + z

    val r = call1(::foo, "2", "3")
}
