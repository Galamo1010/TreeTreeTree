package com.example.treamaster.data

/** Seedling number as the website defines it: 1-6 upper-case letters/digits, "-", then a positive integer (e.g. "A-1"). */
object TreeId {
    private val pattern = Regex("^[A-Z0-9]{1,6}-[1-9][0-9]*$")

    fun isValid(value: String): Boolean = pattern.matches(value)

    /** Trims and upper-cases user input; returns null when it is blank. */
    fun normalize(input: String): String? = input.trim().uppercase().ifEmpty { null }
}
