import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** In-process transport tests; no sockets, services, clocks, sleeps or scheduling assertions. */
public class NvdFeedCacheTest {
    static byte[] gzip;
    static byte[] meta;
    static int ranges;
    static NvdFeedCache.Response response(String range, String etag) {
        if (range == null) return new NvdFeedCache.Response(200, null, null, meta);
        ranges++;
        var bounds = range.substring(6).split("-");
        int start = Integer.parseInt(bounds[0]), end = Integer.parseInt(bounds[1]);
        return new NvdFeedCache.Response(206, "bytes " + start + "-" + end + "/" + gzip.length,
            "stable", Arrays.copyOfRange(gzip, start, end + 1));
    }
    static void expectFailure(Path directory, NvdFeedCache.Transport transport) throws Exception {
        try {
            NvdFeedCache.stage(directory, "modified", transport);
            throw new AssertionError("Expected invalid feed to fail");
        } catch (IOException expected) {
            if (Files.exists(directory.resolve("nvdcve-2.0-modified.json.gz"))) throw new AssertionError("Published invalid gzip");
            if (Files.exists(directory.resolve("nvdcve-2.0-modified.meta"))) throw new AssertionError("Published invalid metadata");
        }
        try (var files = Files.list(directory)) {
            if (files.anyMatch(p -> p.toString().endsWith(".part"))) throw new AssertionError("Partial file leaked");
        }
    }
    public static void main(String[] args) throws Exception {
        byte[] json = new byte[600000];
        new Random(13).nextBytes(json);
        var output = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(output)) { zip.write(json); }
        gzip = output.toByteArray();
        meta = ("gzSize:" + gzip.length + "\nsize:" + json.length + "\nsha256:"
            + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json)) + "\n").getBytes();
        var root = Files.createTempDirectory("nvd-feed-test");
        try {
            var marker = root.resolve("marker");
            var now = java.time.Instant.parse("2026-10-23T12:00:00Z");
            if (NvdFeedCache.incremental(marker, true, now)) throw new AssertionError("Missing marker accepted");
            Files.writeString(marker, "2026-10-22T12:00:00Z");
            if (!NvdFeedCache.incremental(marker, true, now)) throw new AssertionError("Recent data rejected");
            if (NvdFeedCache.incremental(marker, false, now)) throw new AssertionError("Missing database accepted");
            Files.writeString(marker, "2026-10-16T12:00:00Z");
            if (NvdFeedCache.incremental(marker, true, now)) throw new AssertionError("Old data accepted");
            Files.writeString(marker, "malformed");
            if (NvdFeedCache.incremental(marker, true, now)) throw new AssertionError("Malformed marker accepted");
            var valid = root.resolve("valid");
            NvdFeedCache.stage(valid, "modified", (url, range, etag) -> response(range, etag));
            if (ranges != 3) throw new AssertionError("Did not request every chunk including short final chunk");
            ranges = 0;
            NvdFeedCache.stage(valid, "modified", (url, range, etag) -> response(range, etag));
            if (ranges != 0) throw new AssertionError("Redownloaded a verified cached feed");
            Files.write(valid.resolve("nvdcve-2.0-modified.json.gz"), new byte[]{1});
            NvdFeedCache.stage(valid, "modified", (url, range, etag) -> response(range, etag));
            if (ranges != 3) throw new AssertionError("Did not replace corrupt cached feed");
            int[] failures = {1};
            NvdFeedCache.stage(root.resolve("retry"), "modified", (url, range, etag) -> {
                if (range != null && failures[0]-- > 0) throw new IOException("Scripted disconnect");
                return response(range, etag);
            });
            ranges = 0;
            expectFailure(root.resolve("short"), (url, range, etag) -> {
                var part = response(range, etag);
                return range == null ? part : new NvdFeedCache.Response(206, part.range(), part.etag(), new byte[1]);
            });
            if (ranges != 3) throw new AssertionError("Retry bound changed");
            expectFailure(root.resolve("changed"), (url, range, etag) -> {
                var part = response(range, etag);
                return range == null || etag == null ? part : new NvdFeedCache.Response(206, part.range(), "changed", part.body());
            });
            expectFailure(root.resolve("ignored-range"), (url, range, etag) -> {
                var part = response(range, etag);
                return range == null ? part : new NvdFeedCache.Response(200, part.range(), part.etag(), part.body());
            });
            expectFailure(root.resolve("corrupt"), (url, range, etag) -> {
                var part = response(range, etag);
                if (range != null) part.body()[part.body().length - 1] ^= 1;
                return part;
            });
            var originalMeta = meta;
            meta = new String(meta, java.nio.charset.StandardCharsets.ISO_8859_1)
                .replaceAll("sha256:[a-f0-9]+", "sha256:" + "0".repeat(64))
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            expectFailure(root.resolve("wrong-digest"), (url, range, etag) -> response(range, etag));
            meta = originalMeta;
            System.out.println("NVD feed cache transport checks passed");
        } finally {
            try (var files = Files.walk(root)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
