fun main() {
    val safeA = 5
    val total = 7
    val ratio = safeA.toFloat() / total.toFloat()
    val qA1 = (total * ratio).toInt()
    val qA2 = Math.round(total * ratio)
    println("qA1: $qA1, qA2: $qA2")
}
