package com.example.treamaster.data

/** Seedling number as the website defines it: 1-6 upper-case letters/digits, "-", then a positive integer (e.g. "A-1"). */
object TreeId {
    private val pattern = Regex("^[A-Z0-9]{1,6}-[1-9][0-9]*$")

    fun isValid(value: String): Boolean = pattern.matches(value)

    /** Trims and upper-cases user input; returns null when it is blank. */
    fun normalize(input: String): String? = input.trim().uppercase().ifEmpty { null }

    enum class Problem { NO_REGION, BAD_SLOT, SLOT_OUT_OF_RANGE }

    sealed interface Built {
        /** Neither region nor slot given: the tree stays unnumbered. */
        data object Blank : Built
        data class Ok(val id: String) : Built
        data class Invalid(val problem: Problem) : Built
    }

    /**
     * Joins a region and the slot the user typed into a number like "A-3".
     * A region without a slot is treated as unnumbered, since only the slot identifies the tree.
     */
    fun build(region: Region?, slotText: String): Built {
        val slot = slotText.trim()
        if (slot.isEmpty()) return Built.Blank
        if (region == null) return Built.Invalid(Problem.NO_REGION)
        val n = slot.toIntOrNull()?.takeIf { it >= 1 } ?: return Built.Invalid(Problem.BAD_SLOT)
        if (n > region.slotCount) return Built.Invalid(Problem.SLOT_OUT_OF_RANGE)
        return Built.Ok("${region.code}-$n")
    }

    /** Splits "A-3" into ("A", "3"); null for an unnumbered or malformed id. */
    fun split(id: String?): Pair<String, String>? {
        if (id == null || !isValid(id)) return null
        val dash = id.lastIndexOf('-')
        return id.substring(0, dash) to id.substring(dash + 1)
    }
}
