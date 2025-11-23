import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simple standalone HTTP server for tick sightings.
 *
 * How to run in Eclipse:
 * 1. Import this project as "Existing Projects into Workspace".
 * 2. Right-click TickServer.java -> Run As -> Java Application.
 * 3. Open a browser and call:
 *    http://localhost:8080/sightings?from=2019-01-01&to=2020-01-01&location=London
 *    http://localhost:8080/regions?from=2014-01-01&to=2024-01-01
 *    http://localhost:8080/trends?from=2014-01-01&to=2024-01-01&granularity=monthly
 */
public class TickServer {

    // ---- Data model --------------------------------------------------------

    static class TickSighting {
        final String id;
        final LocalDateTime timestamp;
        final String location;
        final String species;
        final String latinName;

        TickSighting(String id, LocalDateTime ts, String location,
                     String species, String latinName) {
            this.id = id;
            this.timestamp = ts;
            this.location = location;
            this.species = species;
            this.latinName = latinName;
        }
    }

    private static final List<TickSighting> ALL_SIGHTINGS = new ArrayList<>();
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;
    private static final ZoneId ZONE = ZoneId.of("Europe/London");

    // ---- Server bootstrap --------------------------------------------------

    public static void main(String[] args) throws Exception {
        loadData();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/sightings", new SightingsHandler());
        server.createContext("/regions", new RegionsHandler());
        server.createContext("/trends", new TrendsHandler());
        server.setExecutor(null); // default executor
        System.out.println("TickServer started on http://localhost:8080");
        server.start();
    }

    // ---- Data loading from CSV --------------------------------------------

    private static void loadData() throws IOException {
        String csvPath = "data/tick_sightings.csv";
        System.out.println("Loading data from " + csvPath);
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(csvPath), StandardCharsets.UTF_8)) {
            String header = reader.readLine(); // skip header
            if (header == null) {
                System.out.println("No data in CSV.");
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                // id,date,location,species,latinName
                String[] parts = splitCsvLine(line);
                if (parts.length < 5) continue;
                String id = parts[0].trim();
                String dateStr = parts[1].trim();
                String location = parts[2].trim();
                String species = parts[3].trim();
                String latin = parts[4].trim();
                if (id.isEmpty() || dateStr.isEmpty()) continue;
                try {
                    LocalDateTime ts = LocalDateTime.parse(dateStr, ISO_DATE_TIME);
                    ALL_SIGHTINGS.add(new TickSighting(id, ts, location, species, latin));
                } catch (Exception e) {
                    // ignore bad rows
                }
            }
        }
        System.out.println("Loaded " + ALL_SIGHTINGS.size() + " sightings.");
    }

    // Very small CSV splitter (does not handle quoted commas, but fine for this file)
    private static String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    // ---- Handlers ----------------------------------------------------------

    // /sightings?from=YYYY-MM-DD&to=YYYY-MM-DD&location=London
    static class SightingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Only GET allowed");
                    return;
                }
                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));
                if (from == null || to == null) {
                    sendText(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendText(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }
                String location = params.get("location");
                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                List<TickSighting> filtered = new ArrayList<>();
                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        if (location == null || location.isBlank()
                                || s.location.equalsIgnoreCase(location)) {
                            filtered.add(s);
                        }
                    }
                }
                filtered.sort(Comparator.comparing(s -> s.timestamp));

                StringBuilder json = new StringBuilder();
                json.append("[");
                for (int i = 0; i < filtered.size(); i++) {
                    TickSighting s = filtered.get(i);
                    if (i > 0) json.append(",");
                    json.append("{")
                        .append("\"id\":\"").append(escapeJson(s.id)).append("\",")
                        .append("\"timestamp\":\"").append(s.timestamp.toString()).append("\",")
                        .append("\"location\":\"").append(escapeJson(s.location)).append("\",")
                        .append("\"species\":\"").append(escapeJson(s.species)).append("\",")
                        .append("\"latinName\":\"").append(escapeJson(s.latinName)).append("\"")
                        .append("}");
                }
                json.append("]");
                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendText(exchange, 500, "Internal server error");
            }
        }
    }

    // /regions?from=YYYY-MM-DD&to=YYYY-MM-DD
    static class RegionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Only GET allowed");
                    return;
                }
                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));
                if (from == null || to == null) {
                    sendText(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendText(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }
                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                Map<String, Integer> counts = new HashMap<>();
                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        String region = (s.location == null || s.location.isBlank())
                                ? "UNKNOWN" : s.location.trim();
                        counts.put(region, counts.getOrDefault(region, 0) + 1);
                    }
                }
                List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
                entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                StringBuilder json = new StringBuilder();
                json.append("[");
                for (int i = 0; i < entries.size(); i++) {
                    Map.Entry<String, Integer> e = entries.get(i);
                    if (i > 0) json.append(",");
                    json.append("{")
                        .append("\"region\":\"").append(escapeJson(e.getKey())).append("\",")
                        .append("\"count\":").append(e.getValue())
                        .append("}");
                }
                json.append("]");
                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendText(exchange, 500, "Internal server error");
            }
        }
    }

    // /trends?from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=monthly|weekly
    static class TrendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Only GET allowed");
                    return;
                }
                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));
                String granularity = params.getOrDefault("granularity", "monthly").toLowerCase();
                if (from == null || to == null) {
                    sendText(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendText(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }
                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                List<TickSighting> filtered = new ArrayList<>();
                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        filtered.add(s);
                    }
                }

                String json;
                if ("weekly".equals(granularity)) {
                    json = buildWeeklyTrend(filtered);
                } else {
                    json = buildMonthlyTrend(filtered);
                }
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendText(exchange, 500, "Internal server error");
            }
        }
    }

    // ---- Trend helpers -----------------------------------------------------

    private static String buildMonthlyTrend(List<TickSighting> sightings) {
        Map<YearMonth, Integer> counts = new HashMap<>();
        for (TickSighting s : sightings) {
            YearMonth ym = YearMonth.from(s.timestamp.atZone(ZONE));
            counts.put(ym, counts.getOrDefault(ym, 0) + 1);
        }
        List<YearMonth> months = new ArrayList<>(counts.keySet());
        months.sort(Comparator.naturalOrder());

        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < months.size(); i++) {
            YearMonth ym = months.get(i);
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"periodLabel\":\"").append(ym.toString()).append("\",")
                .append("\"count\":").append(counts.get(ym))
                .append("}");
        }
        json.append("]");
        return json.toString();
    }

    private static String buildWeeklyTrend(List<TickSighting> sightings) {
        class YearWeek {
            final int year;
            final int week;
            YearWeek(int y, int w) { year = y; week = w; }
            @Override public boolean equals(Object o) {
                if (!(o instanceof YearWeek)) return false;
                YearWeek other = (YearWeek)o;
                return year == other.year && week == other.week;
            }
            @Override public int hashCode() { return year * 100 + week; }
        }

        WeekFields wf = WeekFields.of(Locale.UK);
        Map<YearWeek, Integer> counts = new HashMap<>();
        for (TickSighting s : sightings) {
            int y = s.timestamp.get(wf.weekBasedYear());
            int w = s.timestamp.get(wf.weekOfWeekBasedYear());
            YearWeek yw = new YearWeek(y, w);
            counts.put(yw, counts.getOrDefault(yw, 0) + 1);
        }

        List<YearWeek> weeks = new ArrayList<>(counts.keySet());
        weeks.sort((a, b) -> {
            int c = Integer.compare(a.year, b.year);
            if (c != 0) return c;
            return Integer.compare(a.week, b.week);
        });

        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < weeks.size(); i++) {
            YearWeek yw = weeks.get(i);
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"periodLabel\":\"").append(yw.year).append("-W").append(yw.week).append("\",")
                .append("\"count\":").append(counts.get(yw))
                .append("}");
        }
        json.append("]");
        return json.toString();
    }

    // ---- Utility methods ---------------------------------------------------

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx >= 0) {
                String key = urlDecode(pair.substring(0, idx));
                String val = urlDecode(pair.substring(idx + 1));
                map.put(key, val);
            } else {
                map.put(urlDecode(pair), "");
            }
        }
        return map;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}