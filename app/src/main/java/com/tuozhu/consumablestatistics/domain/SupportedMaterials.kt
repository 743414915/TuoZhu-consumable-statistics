package com.tuozhu.consumablestatistics.domain

object SupportedMaterials {
    const val default = "PETG Basic"

    val all = listOf(
        "PLA Basic",
        "PETG Basic",
        "PLA Silk",
    )

    fun normalize(material: String?): String? {
        val value = material?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = value.lowercase()
        return when {
            "silk" in normalized || "丝绸" in value || "丝滑" in value -> "PLA Silk"
            "petg" in normalized -> "PETG Basic"
            "pla" in normalized -> "PLA Basic"
            else -> all.firstOrNull { it.equals(value, ignoreCase = true) }
        }
    }

    fun isSupported(material: String?): Boolean {
        return material == null || normalize(material) != null
    }
}
