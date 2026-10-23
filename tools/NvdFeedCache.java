import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Transport cache only: Dependency-Check remains responsible for vulnerability analysis. */
public class NvdFeedCache {
    static final String BASE = "https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-";
    static final int CHUNK = 262144;
    record Response(int status, String range, String etag, byte[] body) {}
    interface Transport { Response get(String url, String range, String etag) throws Exception; }

    static Response get(String url, String range, String etag) throws Exception {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        if (range != null) connection.setRequestProperty("Range", range);
        if (etag != null) connection.setRequestProperty("If-Range", etag);
        try {
            int status = connection.getResponseCode();
            byte[] body;
            try (var input = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
                body = input == null ? new byte[0] : input.readNBytes(range == null ? 4096 : CHUNK + 1);
            }
            return new Response(status, connection.getHeaderField("Content-Range"),
                connection.getHeaderField("ETag"), body);
        } finally { connection.disconnect(); }
    }

    static void verify(Path file, Properties meta) throws Exception {
        if (Files.size(file) != Long.parseLong(meta.getProperty("gzSize"))) throw new IOException("Gzip size mismatch");
        var digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (var input = new GZIPInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = input.read(buffer)) != -1) {
                size += n;
                if (size > Long.parseLong(meta.getProperty("size"))) throw new IOException("JSON size exceeded metadata");
                digest.update(buffer, 0, n);
            }
        }
        if (size != Long.parseLong(meta.getProperty("size"))
            || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(meta.getProperty("sha256")))
            throw new IOException("NVD JSON integrity mismatch");
    }

    static void stage(Path directory, String name, Transport transport) throws Exception {
        var prefix = "nvdcve-2.0-" + name;
        var response = transport.get(BASE + name + ".meta", null, null);
        if (response.status() != 200) throw new IOException("Metadata HTTP " + response.status());
        var meta = new Properties();
        meta.load(new ByteArrayInputStream(response.body()));
        long total = Long.parseLong(meta.getProperty("gzSize"));
        if (total <= 0 || total > 512L * 1024 * 1024) throw new IOException("Invalid NVD feed size");
        Files.createDirectories(directory);
        var target = directory.resolve(prefix + ".json.gz");
        boolean cached = false;
        if (Files.isRegularFile(target)) {
            try { verify(target, meta); cached = true; } catch (IOException ignored) { /* Refetch corrupt or changed data. */ }
        }
        if (!cached) {
            var partial = Files.createTempFile(directory, prefix, ".part");
            try {
                String etag = null;
                try (var output = Files.newOutputStream(partial)) {
                    for (long start = 0; start < total; start += CHUNK) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Feed preparation interrupted");
                        long end = Math.min(start + CHUNK - 1, total - 1);
                        for (int attempt = 1; ; attempt++) {
                            try {
                                var part = transport.get(BASE + name + ".json.gz", "bytes=" + start + "-" + end, etag);
                                if (part.status() != 206 || part.body().length != end - start + 1
                                    || !("bytes " + start + "-" + end + "/" + total).equals(part.range())
                                    || part.etag() == null || (etag != null && !etag.equals(part.etag())))
                                    throw new IOException("Invalid or changed NVD range at " + start);
                                etag = part.etag();
                                output.write(part.body());
                                break;
                            } catch (Exception failure) {
                                System.err.println(name + " range " + start + " attempt " + attempt + ": " + failure);
                                if (attempt == 3 || Thread.currentThread().isInterrupted()) throw failure;
                            }
                        }
                    }
                }
                verify(partial, meta);
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(partial); }
        }
        // Publish metadata only after the corresponding gzip has passed all checks.
        Files.write(directory.resolve(prefix + ".meta"), response.body());
        System.out.println("Verified NVD feed " + name + ": " + meta.getProperty("sha256"));
    }

    static boolean incremental(Path marker, boolean databasePresent, Instant now) throws IOException {
        if (!databasePresent || !Files.isRegularFile(marker)) return false;
        try {
            var modified = OffsetDateTime.parse(Files.readString(marker)).toInstant();
            return modified.isAfter(now.minus(Duration.ofDays(7))) && !modified.isAfter(now.plus(Duration.ofDays(1)));
        } catch (java.time.format.DateTimeParseException malformed) { return false; }
    }

    public static void main(String[] args) throws Exception {
        var directory = Path.of(args[1]);
        Files.createDirectories(directory);
        var marker = directory.resolve("last-success-" + args[2]);
        if (args[0].equals("mark")) {
            var meta = new Properties();
            try (var input = Files.newInputStream(directory.resolve("nvdcve-2.0-modified.meta"))) { meta.load(input); }
            Files.writeString(marker, meta.getProperty("lastModifiedDate"));
            return;
        }
        boolean databasePresent;
        try (var files = Files.walk(directory.getParent(), 2)) {
            databasePresent = files.anyMatch(p -> p.getFileName().toString().equals("odc.mv.db"));
        }
        boolean incremental = incremental(marker, databasePresent, Instant.now());
        var names = new ArrayList<String>();
        if (!incremental) for (int year = 2002; year <= Year.now(ZoneOffset.UTC).getValue(); year++) names.add("" + year);
        names.add("modified");
        System.out.println("Preparing NVD feeds: " + names);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var futures = new ArrayList<Future<?>>();
            for (var name : names) futures.add(executor.submit(() -> {
                try { stage(directory, name, NvdFeedCache::get); }
                catch (Exception failure) { throw new CompletionException(failure); }
            }));
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20);
            for (var future : futures) future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } finally { executor.shutdownNow(); }
    }
}
