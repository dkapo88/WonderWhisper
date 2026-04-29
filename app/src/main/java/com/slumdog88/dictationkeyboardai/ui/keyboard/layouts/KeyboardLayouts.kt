package com.slumdog88.dictationkeyboardai.ui.keyboard.layouts

enum class KeyboardRowType {
    Number,
    Letters,
    Bottom
}

sealed interface KeySpec {
    val weight: Float
}

data class SpacerKeySpec(
    override val weight: Float
) : KeySpec

data class CharacterKeySpec(
    val label: String,
    val code: String,
    val secondaryCode: String? = null,
    val secondaryLabel: String? = null,
    val popup: List<String> = emptyList(),
    override val weight: Float = 1f
) : KeySpec

data class TemplateKeySpec(
    val template: TemplateKey,
    override val weight: Float = 1f
) : KeySpec

enum class TemplateKey {
    Shift,
    Delete,
    SymbolsToggle,
    Comma,
    Space,
    Period,
    Enter
}

data class KeyboardRowSpec(
    val type: KeyboardRowType,
    val keys: List<KeySpec>
)

data class KeyboardLayoutDefinition(
    val name: String,
    val description: String = "",
    val category: String? = null,
    val rows: List<KeyboardRowSpec>,
    val includeDefaultNumberRow: Boolean = true,
    val includeDefaultBottomRow: Boolean = true
) {
    fun effectiveRows(
        showNumberRow: Boolean = includeDefaultNumberRow,
        showBottomRow: Boolean = includeDefaultBottomRow
    ): List<KeyboardRowSpec> {
        val resolved = mutableListOf<KeyboardRowSpec>()
        if (showNumberRow) {
            resolved += DefaultRows.numberRow()
        }
        resolved += rows
        if (showBottomRow) {
            resolved += DefaultRows.bottomRow()
        }
        return resolved
    }
}

object DefaultRows {
    fun numberRow(): KeyboardRowSpec = KeyboardRowSpec(
        type = KeyboardRowType.Number,
        keys = listOf(
            charKey("1"), charKey("2"), charKey("3"), charKey("4"), charKey("5"),
            charKey("6"), charKey("7"), charKey("8"), charKey("9"), charKey("0")
        )
    )

    fun bottomRow(): KeyboardRowSpec = KeyboardRowSpec(
        type = KeyboardRowType.Bottom,
        keys = listOf(
            TemplateKeySpec(TemplateKey.SymbolsToggle, weight = 1.5f),
            TemplateKeySpec(TemplateKey.Comma, weight = 1f),
            TemplateKeySpec(TemplateKey.Space, weight = 5f),
            TemplateKeySpec(TemplateKey.Period, weight = 1f),
            TemplateKeySpec(TemplateKey.Enter, weight = 1.5f)
        )
    )
}

object KeyboardLayouts {
    val alphabet = KeyboardLayoutDefinition(
        name = "Alphabet",
        rows = listOf(
            KeyboardRowSpec(
                type = KeyboardRowType.Letters,
                keys = listOf(
                    charKey("q"), charKey("w"),
                    charKey("e", popup = listOf("è", "é", "ê", "ë", "ē", "ė", "ę")),
                    charKey("r"), charKey("t"),
                    charKey("y"), charKey("u", popup = listOf("ū", "ú", "ù", "ü", "û")),
                    charKey("i", popup = listOf("ī", "í", "ì", "ï", "î")),
                    charKey("o", popup = listOf("ō", "ó", "ò", "ö", "ô", "œ", "ø")),
                    charKey("p")
                )
            ),
            KeyboardRowSpec(
                type = KeyboardRowType.Letters,
                keys = listOf(
                    SpacerKeySpec(weight = 0.5f),
                    charKey(
                        label = "a",
                        code = "a",
                        secondaryCode = "@",
                        secondaryLabel = "@",
                        popup = listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā")
                    ),
                    charKey(
                        label = "s",
                        code = "s",
                        secondaryCode = "#",
                        secondaryLabel = "#",
                        popup = listOf("ß", "ś", "š")
                    ),
                    charKey("d", secondaryCode = "&", secondaryLabel = "&"),
                    charKey("f", secondaryCode = "_", secondaryLabel = "_"),
                    charKey("g", secondaryCode = "-", secondaryLabel = "-"),
                    charKey("h", secondaryCode = "+", secondaryLabel = "+"),
                    charKey("j", secondaryCode = "(", secondaryLabel = "("),
                    charKey("k", secondaryCode = ")", secondaryLabel = ")"),
                    charKey("l", secondaryCode = "/", secondaryLabel = "/"),
                    SpacerKeySpec(weight = 0.5f)
                )
            ),
            KeyboardRowSpec(
                type = KeyboardRowType.Letters,
                keys = listOf(
                    TemplateKeySpec(TemplateKey.Shift, weight = 1.5f),
                    charKey("z", secondaryCode = "*", secondaryLabel = "*"),
                    charKey("x", secondaryCode = "\"", secondaryLabel = "\""),
                    charKey(
                        label = "c",
                        code = "c",
                        secondaryCode = "'",
                        secondaryLabel = "'",
                        popup = listOf("'", "ç", "ć", "č")
                    ),
                    charKey("v", secondaryCode = ":", secondaryLabel = ":"),
                    charKey("b", secondaryCode = ";", secondaryLabel = ";"),
                    charKey(
                        label = "n",
                        code = "n",
                        secondaryCode = "!",
                        secondaryLabel = "!",
                        popup = listOf("ñ", "ń")
                    ),
                    charKey("m", secondaryCode = "?", secondaryLabel = "?"),
                    TemplateKeySpec(TemplateKey.Delete, weight = 1.5f)
                )
            )
        )
    )

    val symbols = KeyboardLayoutDefinition(
        name = "Symbols",
        rows = listOf(
            KeyboardRowSpec(
                type = KeyboardRowType.Letters,
                keys = listOf(
                    charKey("@"),
                    charKey("#"),
                    charKey("$", popup = listOf("¢", "£", "€", "¥", "₽")),
                    charKey("%", popup = listOf("‰")),
                    charKey("&"),
                    charKey("-", popup = listOf("_", "–", "—", "·")),
                    charKey("+", popup = listOf("±")),
                    charKey("(", popup = listOf("<", "{", "[")),
                    charKey(")", popup = listOf(">", "}", "]"))
                )
            ),
            KeyboardRowSpec(
                type = KeyboardRowType.Letters,
                keys = listOf(
                    charKey("~"),
                    charKey("=", popup = listOf("≠", "≈", "∞")),
                    charKey("\\"),
                    charKey("/"),
                    charKey("*", popup = listOf("†", "‡", "★")),
                    charKey("\""),
                    charKey("'"),
                    charKey(":"),
                    charKey(";")
                )
            ),
            KeyboardRowSpec(
                type = KeyboardRowType.Letters,
                keys = listOf(
                    TemplateKeySpec(TemplateKey.Shift, weight = 1.5f),
                    charKey("!", popup = listOf("¡")),
                    charKey("?", popup = listOf("¿")),
                    charKey(","),
                    charKey(".", popup = listOf("…")),
                    TemplateKeySpec(TemplateKey.Delete, weight = 1.5f)
                )
            )
        ),
        includeDefaultNumberRow = true,
        includeDefaultBottomRow = true
    )
}

private fun charKey(
    label: String,
    code: String = label,
    secondaryCode: String? = null,
    secondaryLabel: String? = null,
    popup: List<String> = emptyList(),
    weight: Float = 1f
) = CharacterKeySpec(
    label = label,
    code = code,
    secondaryCode = secondaryCode,
    secondaryLabel = secondaryLabel,
    popup = popup,
    weight = weight
)
