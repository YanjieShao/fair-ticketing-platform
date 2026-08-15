import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Virtual-thread HTTP stampede against a running Fair Ticketing API.
 *
 * Usage: java --enable-preview is not required on 21.
 *   java CheckoutLoadClient.java http://localhost:8080 10000 30000
 */
public class CheckoutLoadClient {

    private static final Pattern TIER = Pattern.compile("\"tierId\"\\s*:\\s*(\\d+)");
    private static final Pattern STOCK = Pattern.compile("\"stock\"\\s*:\\s*(\\d+)");

    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0] : "http://localhost:8080";
        int buyers = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int stock = args.length > 2 ? Integer.parseInt(args[2]) : 30_000;

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(60))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        System.err.println("Creating fixture: " + buyers + " buyers, " + stock + " tickets at " + base);
        String fixtureJson = post(http, base + "/api/admin/load-test/fixtures",
                "{\"buyers\":" + buyers + ",\"stock\":" + stock + "}", null);
        long tierId = Long.parseLong(match(TIER, fixtureJson));
        int confirmedStock = Integer.parseInt(match(STOCK, fixtureJson));
        List<String> tokens = parseTokens(fixtureJson);
        if (tokens.size() != buyers) {
            throw new IllegalStateException("expected " + buyers + " tokens, got " + tokens.size());
        }

        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(buyers);
        AtomicInteger created = new AtomicInteger();
        ConcurrentHashMap<Integer, Integer> statuses = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> codes = new ConcurrentHashMap<>();
        long[] latencies = new long[buyers];

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < buyers; i++) {
                final int index = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        long t0 = System.nanoTime();
                        HttpResponse<String> response = http.send(
                                checkout(base, tokens.get(index), tierId, index),
                                HttpResponse.BodyHandlers.ofString());
                        latencies[index] = System.nanoTime() - t0;
                        statuses.merge(response.statusCode(), 1, Integer::sum);
                        if (response.statusCode() == 201) {
                            created.incrementAndGet();
                        } else {
                            codes.merge(extractCode(response.body()), 1, Integer::sum);
                        }
                    } catch (Exception error) {
                        latencies[index] = -1;
                        codes.merge(error.getClass().getSimpleName(), 1, Integer::sum);
                    } finally {
                        done.countDown();
                    }
                });
            }

            if (!ready.await(2, TimeUnit.MINUTES)) {
                throw new IllegalStateException("buyers did not all reach the start line");
            }
            long wallStart = System.nanoTime();
            start.countDown();
            if (!done.await(15, TimeUnit.MINUTES)) {
                throw new IllegalStateException("stampede did not finish in 15 minutes");
            }
            long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);

            String resultJson = get(http, base + "/api/admin/load-test/result/" + tierId);
            System.out.println("profile: buyers=" + buyers + " stock=" + confirmedStock);
            System.out.println("wall_ms: " + wallMs);
            System.out.println("http_201: " + created.get());
            System.out.println("http_statuses: " + statuses);
            System.out.println("business_codes: " + codes);
            System.out.println("latency_ms_p50: " + percentileMs(latencies, 50));
            System.out.println("latency_ms_p95: " + percentileMs(latencies, 95));
            System.out.println("latency_ms_p99: " + percentileMs(latencies, 99));
            System.out.println("latency_ms_max: " + percentileMs(latencies, 100));
            System.out.println("result: " + resultJson.replaceAll("\\s+", " "));
            int expectedSold = Math.min(buyers, confirmedStock);
            boolean oversold = resultJson.contains("\"oversold\":true");
            boolean soldEnough = created.get() <= confirmedStock;
            System.out.println("zero_oversell: " + (!oversold && soldEnough));
            System.out.println("expected_sold: " + expectedSold);
            System.out.println("p99_under_200ms: " + (percentileMs(latencies, 99) < 200));
        }
    }

    private static HttpRequest checkout(String base, String token, long tierId, int index) {
        return HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "load-" + index)
                .POST(HttpRequest.BodyPublishers.ofString("{\"tierId\":" + tierId + ",\"quantity\":1}"))
                .build();
    }

    private static String post(HttpClient http, String url, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(url + " -> " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    private static String get(HttpClient http, String url) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(url + " -> " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    private static String match(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("missing " + pattern + " in " + json.substring(0, Math.min(200, json.length())));
        }
        return matcher.group(1);
    }

    static List<String> parseTokens(String json) {
        int label = json.indexOf("\"tokens\"");
        if (label < 0) {
            throw new IllegalStateException("no tokens array");
        }
        int start = json.indexOf('[', label) + 1;
        int end = json.indexOf(']', start);
        String body = json.substring(start, end).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(body.split(","))
                .map(part -> part.trim().replace("\"", ""))
                .toList();
    }

    static String extractCode(String body) {
        Matcher matcher = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : "UNKNOWN";
    }

    static long percentileMs(long[] nanos, int percentile) {
        List<Long> ok = new ArrayList<>();
        for (long sample : nanos) {
            if (sample >= 0) {
                ok.add(sample);
            }
        }
        if (ok.isEmpty()) {
            return -1;
        }
        Collections.sort(ok);
        int index = Math.min(ok.size() - 1, (int) Math.ceil(percentile / 100.0 * ok.size()) - 1);
        return TimeUnit.NANOSECONDS.toMillis(ok.get(Math.max(0, index)));
    }
}
