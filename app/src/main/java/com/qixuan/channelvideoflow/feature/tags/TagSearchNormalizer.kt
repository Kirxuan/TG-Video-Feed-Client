package com.qixuan.channelvideoflow.feature.tags

import java.text.Normalizer
import java.util.Locale

/** Normalizes a local tag-search value without introducing a data-layer query. */
internal fun normalizeTagSearchQuery(value: String): String {
    val trimmed = value.trim()
    val withoutOptionalHash = trimmed.removePrefix("#").trimStart()
    return Normalizer.normalize(withoutOptionalHash, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
}

internal data class SearchableTag(
    val item: com.qixuan.channelvideoflow.model.video.TagSummary,
    val normalizedDisplayName: String = normalizeTagSearchQuery(item.displayName),
    val normalizedName: String = normalizeTagSearchQuery(item.normalizedName),
) {
    fun matches(normalizedQuery: String): Boolean =
        normalizedQuery.isEmpty() ||
            normalizedDisplayName.contains(normalizedQuery) ||
            normalizedName.contains(normalizedQuery)
}
