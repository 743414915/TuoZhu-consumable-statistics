package com.tuozhu.consumablestatistics.domain

object SupportedMaterials {
    const val default = "PETG Basic"

    val builtIn = listOf(
        "PLA Basic",
        "PETG Basic",
        "PLA Silk",
    )

    fun all(custom: List<String> = emptyList()): List<String> {
        return builtIn + custom.filter { it.isNotBlank() && it !in builtIn }
    }

    fun normalize(material: String?, custom: List<String> = emptyList()): String? {
        val value = material?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = value.lowercase()
        return when {
            "silk" in normalized || "丝绸" in value || "丝滑" in value -> "PLA Silk"
            "petg" in normalized -> "PETG Basic"
            "pla" in normalized -> "PLA Basic"
            else -> {
                builtIn.firstOrNull { it.equals(value, ignoreCase = true) }
                    ?: custom.firstOrNull { it.equals(value, ignoreCase = true) }
            }
        }
    }

    fun isSupported(material: String?, custom: List<String> = emptyList()): Boolean {
        return material == null || normalize(material, custom) != null
    }
}
