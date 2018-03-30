package ai.zenkai.zenkai

val NON_LETTER_CHARACTERS by lazy { """[^\p{L}]+""".toRegex() }

val NON_LETTER_CHARACTERS_EXCEPT_SPACES by lazy { """[^\p{L} ]+""".toRegex() }

val NON_WORD_CHARACTERS_EXCEPT_SPACES by lazy { """[^\p{L}\d ]+""".toRegex() }

val DIGIT by lazy { """\d""".toRegex() }

val NUMBER by lazy { """\p{N}""".toRegex() }

val DIACRITICAL_MARKS by lazy { """\p{InCombiningDiacriticalMarks}+""".toRegex() }