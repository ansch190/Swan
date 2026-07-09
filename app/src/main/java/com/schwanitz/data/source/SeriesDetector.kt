package com.schwanitz.data.source

object SeriesDetector {

    data class SeriesResult(
        val seriesName: String,
        val volumes: List<VolumeInfo>
    )

    data class VolumeInfo(
        val albumName: String,
        val volumeNumber: Int
    )

    private val patterns = listOf(
        Regex("""^(.+?)\s+Vol\.?\s*(\d+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.+?)\s+Volume\s+(\d+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.+?)\s+Part\s+(\d+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.+?)\s+#(\d+)$"""),
        Regex("""^(.+?)\s+(\d+)$""")
    )

    fun detectSeries(albumNames: Set<String>): List<SeriesResult> {
        val candidates = mutableMapOf<String, MutableList<VolumeInfo>>()

        for (albumName in albumNames) {
            for (pattern in patterns) {
                val matchResult = pattern.find(albumName)
                if (matchResult != null) {
                    val prefix = matchResult.groupValues[1].trim()
                    val number = matchResult.groupValues[2].toIntOrNull()
                    if (prefix.isNotBlank() && number != null) {
                        candidates.getOrPut(prefix) { mutableListOf() }
                            .add(VolumeInfo(albumName, number))
                    }
                    break
                }
            }
        }

        return candidates
            .filter { it.value.size >= 2 }
            .map { (seriesName, volumes) ->
                SeriesResult(seriesName, volumes.sortedBy { it.volumeNumber })
            }
            .sortedBy { it.seriesName }
    }
}
