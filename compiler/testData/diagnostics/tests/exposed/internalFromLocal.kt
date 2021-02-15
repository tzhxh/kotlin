// FIR_IDENTICAL
interface Your

class My {
    internal val x = object : Your {}

    internal fun foo() = {
        class Local
        Local()
    }()
}
