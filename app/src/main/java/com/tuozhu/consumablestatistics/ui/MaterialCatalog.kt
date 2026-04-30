package com.tuozhu.consumablestatistics.ui

import com.tuozhu.consumablestatistics.domain.SupportedMaterials

data class FilamentMaterialOption(
    val label: String,
    val shortName: String,
    val defaultColorHex: String,
    val defaultThresholdGrams: Int = 150,
    val isCustom: Boolean = false,
)

private val builtInColors = listOf("#F5D06F", "#59B6B2", "#D9B5FF", "#EFB07C", "#7EB8C9", "#C9A7D4", "#A5C983", "#E88B8B")

fun buildAllMaterialOptions(customMaterials: List<String> = emptyList()): List<FilamentMaterialOption> {
    val builtIn = SupportedMaterials.builtIn.mapIndexed { index, name ->
        FilamentMaterialOption(
            label = name,
            shortName = name,
            defaultColorHex = builtInColors[index % builtInColors.size],
            defaultThresholdGrams = 150,
            isCustom = false,
        )
    }
    val custom = customMaterials.mapIndexed { index, name ->
        FilamentMaterialOption(
            label = "$name（自定义）",
            shortName = name,
            defaultColorHex = builtInColors[(builtIn.size + index) % builtInColors.size],
            defaultThresholdGrams = 150,
            isCustom = true,
        )
    }
    return builtIn + custom
}

fun defaultAddRollMaterialIndex(customMaterials: List<String> = emptyList()): Int {
    val all = buildAllMaterialOptions(customMaterials)
    return all.indexOfFirst { it.shortName == "PETG Basic" }.takeIf { it >= 0 } ?: 0
}
