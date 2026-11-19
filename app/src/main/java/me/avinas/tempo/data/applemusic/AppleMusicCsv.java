package me.avinas.tempo.data.applemusic;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppleMusicCsv implements Closeable {
    private static final int MAX_RECORD_CHARS = 256 * 1024;
    private static final String[] TITLES = {"Song Name", "Content Name", "Track Name"};
    private static final String[] START = {"Event Start Timestamp"};
    private static final String[] END = {"Event End Timestamp"};
    private static final String[] PLAYED = {"Play Duration Milliseconds", "Play Duration In Milliseconds"};

    private final BufferedReader reader;
    private final Map<String, Integer> columns = new HashMap<>();

    AppleMusicCsv(Reader input) throws IOException {
        reader = new BufferedReader(input);
        reader.mark(1);
        if (reader.read() != '\uFEFF') reader.reset();

        List<String> header = readRecord();
        if (header == null) return;
        for (int index = 0; index < header.size(); index++) {
            columns.putIfAbsent(normalize(header.get(index)), index);
        }
    }

    boolean isPlayActivity() {
        return (has(TITLES) || has("Track Description")) && has(PLAYED) && (has(START) || has(END));
    }

    private boolean has(String... names) {
        for (String name : names) {
            if (columns.containsKey(normalize(name))) return true;
        }
        return false;
    }

    Row next() throws IOException {
        List<String> values = readRecord();
        while (values != null && values.size() == 1 && values.get(0).trim().isEmpty()) {
            values = readRecord();
        }
        return values == null ? null : new Row(values);
    }

    private List<String> readRecord() throws IOException {
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        int count = 0;

        while (true) {
            int ch = reader.read();
            if (ch < 0) {
                if (quoted) throw new IOException("Unterminated Apple Music CSV field");
                if (count == 0) return null;
                break;
            }
            if (++count > MAX_RECORD_CHARS) throw new IOException("Apple Music CSV record is too large");

            if (quoted) {
                if (ch == '"') {
                    reader.mark(1);
                    if (reader.read() == '"') {
                        field.append('"');
                        if (++count > MAX_RECORD_CHARS) throw new IOException("Apple Music CSV record is too large");
                    } else {
                        reader.reset();
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append((char) ch);
                }
            } else if (ch == ',') {
                values.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') {
                    reader.mark(1);
                    if (reader.read() != '\n') reader.reset();
                }
                break;
            } else if (ch == '"' && field.length() == 0 && !closedQuote) {
                quoted = true;
            } else if (closedQuote || ch == '"') {
                throw new IOException("Invalid quoting in Apple Music CSV");
            } else {
                field.append((char) ch);
            }
        }
        values.add(field.toString());
        return values;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    final class Row {
        private final List<String> values;

        Row(List<String> values) {
            this.values = values;
        }

        String get(String... names) {
            for (String name : names) {
                Integer index = columns.get(normalize(name));
                if (index != null && index < values.size()) {
                    String value = values.get(index).trim();
                    if (!value.isEmpty()) return value;
                }
            }
            return "";
        }

        String title() {
            String title = get(TITLES);
            if (!title.isEmpty()) return title;
            String description = get("Track Description");
            int separator = description.indexOf(" - ");
            return separator < 0 ? "" : description.substring(separator + 3).trim();
        }

        String artist() {
            String artist = get("Artist Name", "Track Artist", "Artist");
            if (!artist.isEmpty()) return artist;
            String description = get("Track Description");
            int separator = description.indexOf(" - ");
            String title = get(TITLES);
            if (separator >= 0 && (title.isEmpty() ||
                    description.substring(separator + 3).trim().equalsIgnoreCase(title))) {
                return description.substring(0, separator).trim();
            }
            // The album container's credit is relevant only for a play from that album.
            String album = album();
            String containerArtist = get("Container Artist Name");
            if (!album.isEmpty() && get("Container Type").equalsIgnoreCase("ALBUM") &&
                    album.equalsIgnoreCase(get("Container Album Name")) &&
                    !containerArtist.equalsIgnoreCase("Various Artists")) {
                return containerArtist;
            }
            return "";
        }

        String album() {
            return get("Album Name", "Album");
        }

        boolean isPlayback() {
            String type = get("Event Type");
            String reason = get("End Reason Type");
            String mediaType = get("Media Type");
            String itemType = get("Item Type", "Content Specific Type");
            return (type.isEmpty() || type.equalsIgnoreCase("PLAY_END"))
                && !reason.equalsIgnoreCase("SCRUB_END")
                && !reason.equalsIgnoreCase("FAILED_TO_LOAD")
                && !mediaType.equalsIgnoreCase("VIDEO")
                && !itemType.equalsIgnoreCase("PODCAST")
                && !itemType.equalsIgnoreCase("AUDIOBOOK")
                && !itemType.equalsIgnoreCase("NON_SONG_CLIP")
                && !itemType.equalsIgnoreCase("AD");
        }

        Long playedMs() {
            return duration(get(PLAYED));
        }

        Long trackMs() {
            Long value = duration(get("Media Duration In Milliseconds", "Media Duration Milliseconds"));
            return value != null && value > 0 ? value : null;
        }

        Long startMs(long playedMs) {
            Long start = timestamp(get(START));
            if (start != null) return start;
            Long end = endMs();
            return end == null ? null : end - playedMs;
        }

        Long endMs() {
            return timestamp(get(END));
        }

        Integer offsetMinutes() {
            try {
                int seconds = Integer.parseInt(get("UTC Offset In Seconds"));
                return seconds >= -64800 && seconds <= 64800 ? seconds / 60 : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static Long duration(String value) {
        try {
            long millis = Long.parseLong(value);
            return millis >= 0 && millis <= 86400000 ? millis : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long timestamp(String value) {
        if (value.isEmpty()) return null;
        // Apple also exports UTC timestamps with a space between date and time.
        String iso = value.endsWith(" UTC") ? value.substring(0, value.length() - 4) + "Z" : value;
        if (iso.length() > 10 && iso.charAt(10) == ' ') {
            iso = iso.substring(0, 10) + "T" + iso.substring(11);
        }
        iso = iso.replace(" ", "");
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (DateTimeException | ArithmeticException ignored) {
            try {
                Instant instant = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
                return instant.toEpochMilli();
            } catch (DateTimeException | ArithmeticException invalid) {
                return null;
            }
        }
    }
}
