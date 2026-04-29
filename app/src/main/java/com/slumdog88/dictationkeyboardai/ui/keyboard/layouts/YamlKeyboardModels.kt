package com.slumdog88.dictationkeyboardai.ui.keyboard.layouts

import android.content.Context
import android.util.Log
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.Locale

@Serializable
data class YamlKeyboardLayoutDefinition(
    val name: String,
    val description: String = "",
    val category: String? = null,
    val includeDefaultNumberRow: Boolean = true,
    val includeDefaultBottomRow: Boolean = true,
    val rows: List<YamlKeyboardRow>
)

@Serializable
data class YamlKeyboardRow(
    val type: YamlRowType,
    val keys: List<YamlKeySpec>
)

@Serializable
enum class YamlRowType {
    @SerialName("number") Number,
    @SerialName("letters") Letters,
    @SerialName("bottom") Bottom
}

@Serializable
data class YamlKeySpec(
    val label: String? = null,
    val code: String? = null,
    val secondaryCode: String? = null,
    val secondaryLabel: String? = null,
    val popup: List<String> = emptyList(),
    val weight: Float? = null,
    val template: String? = null
)

/**
 * Loads keyboard layout YAML definitions inspired by the FUTO LXX stack.
 * The goal is to let designers tweak layouts in assets without recompiling code.
 */
class KeyboardLayoutRepository(private val context: Context) {
    private val yaml = Yaml(configuration = YamlConfiguration(allowAnchorsAndAliases = true))

    private val cachedLayouts: List<KeyboardLayoutDefinition> by lazy {
        loadLayoutsFromAssets()
    }

    fun availableLayouts(): List<KeyboardLayoutDefinition> = cachedLayouts

    fun getLayout(name: String): KeyboardLayoutDefinition? {
        return cachedLayouts.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun defaultAlphabetLayout(): KeyboardLayoutDefinition =
        category("alphabet")
            ?: cachedLayouts.firstOrNull { it.name.lowercase(Locale.ROOT).contains("default") }
            ?: cachedLayouts.firstOrNull()
            ?: KeyboardLayouts.alphabet

    fun defaultSymbolsLayout(): KeyboardLayoutDefinition =
        category("symbols") ?: KeyboardLayouts.symbols

    fun defaultAltSymbolsLayout(): KeyboardLayoutDefinition =
        category("symbols_alt") ?: KeyboardLayouts.symbols

    private fun category(name: String): KeyboardLayoutDefinition? {
        val target = name.lowercase(Locale.ROOT)
        return cachedLayouts.firstOrNull {
            it.name.lowercase(Locale.ROOT) == target || it.description.lowercase(Locale.ROOT).contains(target)
                    || it.category.equals(target, ignoreCase = true)
        }
    }

    private fun loadLayoutsFromAssets(): List<KeyboardLayoutDefinition> {
        val assetManager = context.assets
        val directory = "layouts"
        val yamlFiles = try {
            assetManager.list(directory)?.filter { it.endsWith(".yaml") || it.endsWith(".yml") }
        } catch (e: Exception) {
            Log.e("KeyboardLayoutRepo", "Failed to list layout assets", e)
            null
        } ?: emptyList()

        if (yamlFiles.isEmpty()) return emptyList()

        return yamlFiles.mapNotNull { fileName ->
            val path = "$directory/$fileName"
            try {
                val text = assetManager.open(path).use { it.bufferedReader().readText() }
                val parsed = yaml.decodeFromString(YamlKeyboardLayoutDefinition.serializer(), text)
                parsed.toLayoutDefinition()
            } catch (e: SerializationException) {
                Log.e("KeyboardLayoutRepo", "Failed to parse layout $fileName: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("KeyboardLayoutRepo", "Error loading layout $fileName", e)
                null
            }
        }
    }
}

private fun YamlKeyboardLayoutDefinition.toLayoutDefinition(): KeyboardLayoutDefinition {
    val rows = rows.map { row ->
        KeyboardRowSpec(
            type = when (row.type) {
                YamlRowType.Number -> KeyboardRowType.Number
                YamlRowType.Letters -> KeyboardRowType.Letters
                YamlRowType.Bottom -> KeyboardRowType.Bottom
            },
            keys = row.keys.mapNotNull { it.toKeySpec() }
        )
    }

    return KeyboardLayoutDefinition(
        name = name,
        description = description,
        category = category,
        rows = rows,
        includeDefaultNumberRow = includeDefaultNumberRow,
        includeDefaultBottomRow = includeDefaultBottomRow
    )
}

private fun YamlKeySpec.toKeySpec(): KeySpec? {
    template?.let { templateName ->
        if (templateName.equals("Spacer", ignoreCase = true)) {
            return SpacerKeySpec(weight ?: 1f)
        }

        val resolvedTemplate = TemplateKey.entries.firstOrNull {
            it.name.equals(templateName, ignoreCase = true)
        }
        if (resolvedTemplate != null) {
            return TemplateKeySpec(resolvedTemplate, weight ?: 1f)
        }
    }

    val resolvedLabel = label ?: code
    val resolvedCode = code ?: label
    if (resolvedLabel == null || resolvedCode == null) return null

    return CharacterKeySpec(
        label = resolvedLabel,
        code = resolvedCode,
        secondaryCode = secondaryCode,
        secondaryLabel = secondaryLabel,
        popup = popup,
        weight = weight ?: 1f
    )
}
