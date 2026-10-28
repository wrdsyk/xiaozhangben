package com.jizhang.domain

/**
 * A quick-fill note under the note field. Either learned (a note used [FREQUENT_MIN] or more
 * times in this category) or added by hand ([custom]). Removing a learned one hides it.
 */
data class NoteChip(val text: String, val custom: Boolean) {
    companion object {
        const val FREQUENT_MIN = 3

        /** At most this many learned chips; the user's own ones always show. */
        const val MAX = 12

        /** How many frequent notes to fetch, so hidden ones don't crowd out the rest. */
        const val FETCH = MAX * 4

        /**
         * Learned notes by frequency (at most [MAX] after dropping [hidden]), then the user's own;
         * blanks and duplicates dropped.
         */
        fun merge(frequent: List<String>, custom: List<String>, hidden: Set<String>): List<NoteChip> {
            val seen = HashSet<String>()
            val out = ArrayList<NoteChip>()
            for (t in frequent) {
                if (out.size >= MAX) break
                if (t.isNotEmpty() && t !in hidden && seen.add(t)) out += NoteChip(t, custom = false)
            }
            for (t in custom) if (t.isNotEmpty() && seen.add(t)) out += NoteChip(t, custom = true)
            return out
        }
    }
}
