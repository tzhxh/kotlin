class C(val expected: Int) {
    fun memberVararg(i: Int, vararg s: String) {
    }

    fun memberDefault(i: Int, s: String = "") {
    }

    fun memberBoth(i: Int, s: String = "", vararg t: String) {
    }
}

fun C.extensionVararg(i: Int, vararg s: String) {
    memberVararg(i, *s)
}

fun C.extensionDefault(i: Int, s: String = "") {
    memberDefault(i, s)
}

fun C.extensionBoth(i: Int, s: String = "", vararg t: String) {
    memberBoth(i, s, *t)
}

fun test(f: C.(Int) -> Unit, p: Int) = C(p).f(p)

fun box(): String {

    test(C::memberVararg, 43)
    test(C::memberDefault, 43)
    test(C::memberBoth, 43)
    test(C::extensionVararg, 43)
    test(C::extensionDefault, 43)
    test(C::extensionBoth, 43)

    return "OK"
}
