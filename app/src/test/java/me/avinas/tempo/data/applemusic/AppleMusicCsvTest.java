package me.avinas.tempo.data.applemusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import org.junit.Test;

public class AppleMusicCsvTest {
    @Test
    public void readsQuotedLegacyActivityWithOriginalDurationAndOffset() throws IOException {
        String input = "\uFEFFContent Name,Artist Name,Album Name,Event Start Timestamp,"
            + "Play Duration Milliseconds,Media Duration In Milliseconds,UTC Offset In Seconds\r\n"
            + "\"A \"\"Song\"\", Part II\",\"Tyler, the Creator\",\"Side A\r\nSide B\","
            + "2024-01-02 03:04:05.123 UTC,65432,240000,19800\r\n";

        try (AppleMusicCsv csv = new AppleMusicCsv(new StringReader(input))) {
            assertTrue(csv.isPlayActivity());
            AppleMusicCsv.Row row = csv.next();
            assertTrue(row.isPlayback());
            assertEquals("A \"Song\", Part II", row.title());
            assertEquals("Tyler, the Creator", row.artist());
            assertEquals("Side A\r\nSide B", row.album());
            assertEquals(Long.valueOf(65432), row.playedMs());
            assertEquals(Long.valueOf(240000), row.trackMs());
            assertEquals(Integer.valueOf(330), row.offsetMinutes());
            assertEquals(Long.valueOf(Instant.parse("2024-01-02T03:04:05.123Z").toEpochMilli()),
                row.startMs(row.playedMs()));
            assertNull(csv.next());
        }
    }

    @Test
    public void readsNewActivityAndFiltersNonPlaybackEvents() throws IOException {
        String input = "Song Name,Album Name,Container Type,Container Album Name,Container Artist Name,"
            + "Event End Timestamp,Play Duration Milliseconds,Event Type,End Reason Type\n"
            + "Song,Record,ALBUM,Record,Artist,2024-01-02T03:05:00-05:00,60000,PLAY_END,NATURAL_END_OF_TRACK\n"
            + "Song,Record,PLAYLIST,Record,Curator,2024-01-02T03:05:00Z,50000,LYRIC_DISPLAY,\n"
            + "Song,Record,ALBUM,Record,Various Artists,2024-01-02T03:05:00Z,50000,PLAY_END,SCRUB_END";

        try (AppleMusicCsv csv = new AppleMusicCsv(new StringReader(input))) {
            assertTrue(csv.isPlayActivity());
            AppleMusicCsv.Row play = csv.next();
            assertTrue(play.isPlayback());
            assertEquals("Artist", play.artist());
            assertEquals(Long.valueOf(Instant.parse("2024-01-02T08:04:00Z").toEpochMilli()),
                play.startMs(play.playedMs()));
            AppleMusicCsv.Row lyric = csv.next();
            assertFalse(lyric.isPlayback());
            assertEquals("", lyric.artist());
            AppleMusicCsv.Row seek = csv.next();
            assertFalse(seek.isPlayback());
            assertEquals("", seek.artist());
            assertNull(csv.next());
        }
    }

    @Test
    public void readsDailyCreditsWithoutAcceptingAggregatesAsActivity() throws IOException {
        String input = "Track Description,Date Played,Play Count,Play Duration Milliseconds\n"
            + "Artist - Song - Live,20240102,3,600000\n";
        try (AppleMusicCsv csv = new AppleMusicCsv(new StringReader(input))) {
            assertFalse(csv.isPlayActivity());
            AppleMusicCsv.Row row = csv.next();
            assertEquals("Artist", row.artist());
            assertEquals("Song - Live", row.title());
        }
    }

    @Test(expected = IOException.class)
    public void rejectsUnterminatedQuotedRow() throws IOException {
        try (AppleMusicCsv csv = new AppleMusicCsv(new StringReader("Song Name,Artist Name\n\"Song"))) {
            csv.next();
        }
    }
}
