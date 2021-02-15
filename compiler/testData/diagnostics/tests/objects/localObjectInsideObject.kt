// FIR_IDENTICAL
// !CHECK_TYPE

fun foo() {
    val a = object {
        val b = object {
            val c = 42
        }
    }

    <!INAPPLICABLE_CANDIDATE!>checkSubtype<!><Int>(a.b.<!UNRESOLVED_REFERENCE!>c<!>)
}