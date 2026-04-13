package com.tuozhu.consumablestatistics.ui

import com.tuozhu.consumablestatistics.domain.SupportedMaterials

data class FilamentMaterialOption(
    val label: String,
    val shortName: String,
    val defaultColorHex: String,
    val defaultThresholdGrams: Int = 150,
)

val BambuMaterialOptions = listOf(
    FilamentMaterialOption(SupportedMaterials.all[0], SupportedMaterials.all[0], "#F5D06F"),
    FilamentMaterialOption(SupportedMaterials.all[1], SupportedMaterials.all[1], "#59B6B2"),
    FilamentMaterialOption(SupportedMaterials.all[2], SupportedMaterials.all[2], "#D9B5FF"),
)

fun defaultAddRollMaterialIndex(): Int {
    return BambuMaterialOptions.indexOfFirst { it.shortName == "PETG Basic" }
        .takeIf { it >= 0 }
        ?: 0
}

val DefaultBambuMaterialIndex = BambuMaterialOptions.indexOfFirst {
    it.shortName == SupportedMaterials.default
}.takeIf { it >= 0 } ?: 0
