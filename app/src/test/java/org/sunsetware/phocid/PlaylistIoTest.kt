package org.sunsetware.phocid

import kotlin.collections.joinToString
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.sunsetware.phocid.data.PlaylistIoSettings
import org.sunsetware.phocid.data.parseM3u

class PlaylistIoTest {
    fun testParseM3u(
        m3u: String,
        libraryTrackPaths: Set<String>,
        settings: PlaylistIoSettings,
        expected: List<String>,
    ) {
        val actual =
            parseM3u(
                    "",
                    m3u.toByteArray(Charsets.UTF_8),
                    libraryTrackPaths,
                    settings,
                    Charsets.UTF_8.name(),
                )
                .entries
                .map { it.path }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected)
    }

    val parseM3uLibraryTrackPaths = setOf("dir/a", "dir/dir2/b", "c", "dir3/d")

    @Test
    fun parseM3u_MatchLocation() {
        testParseM3u(
            m3u =
                listOf(" dir/a ", "dir/dir2/b", "#c", "D:\\D", "e", "http://invalid")
                    .joinToString("\r\n"),
            libraryTrackPaths = parseM3uLibraryTrackPaths,
            settings = PlaylistIoSettings(ignoreLocation = false),
            expected = listOf("dir/a", "dir/dir2/b"),
        )
    }

    @Test
    fun parseM3u_DoNotRemoveInvalid() {
        testParseM3u(
            m3u =
                listOf(" dir/a ", "dir/dir2/b", "#c", "D:\\D", "e", "http://invalid")
                    .joinToString("\r\n"),
            libraryTrackPaths = parseM3uLibraryTrackPaths,
            settings = PlaylistIoSettings(ignoreLocation = false, removeInvalid = false),
            expected = listOf("dir/a", "dir/dir2/b", "D:/D", "e", "http://invalid"),
        )
    }

    @Test
    fun parseM3u_IgnoreLocation() {
        testParseM3u(
            m3u =
                listOf(" ../a ", "http://example.com/b", "#c", "D:\\D", "e", "http://invalid")
                    .joinToString("\r\n"),
            libraryTrackPaths = parseM3uLibraryTrackPaths,
            settings = PlaylistIoSettings(),
            expected = listOf("dir/a", "dir/dir2/b", "dir3/d"),
        )
    }

    @Test
    fun parseM3u_RoundTrip() {
        testParseM3u(
            m3u =
                parseM3u(
                        "",
                        parseM3uLibraryTrackPaths.joinToString("\r\n").toByteArray(Charsets.UTF_8),
                        parseM3uLibraryTrackPaths,
                        PlaylistIoSettings(),
                        Charsets.UTF_8.name(),
                    )
                    .entries
                    .joinToString("\n") { it.path },
            libraryTrackPaths = parseM3uLibraryTrackPaths,
            settings = PlaylistIoSettings(),
            expected = parseM3uLibraryTrackPaths.toList(),
        )
    }
}
