import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
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


public class TickServer {

    // Cross-platform path to the CSV file:
    // <project root>/data/tick_sightings.csv
    private static final String CSV_PATH = Paths
            .get(System.getProperty("user.dir"), "data", "tick_sightings.csv")
            .toString();

    // ---- Data model ----------------------------------------------------

    static class TickSighting {
        final String id;
        final LocalDateTime timestamp;
        final String location;
        final String species;
        final String latinName;

        TickSighting(String id,
                     LocalDateTime timestamp,
                     String location,
                     String species,
                     String latinName) {
            this.id = id;
            this.timestamp = timestamp;
            this.location = location;
            this.species = species;
            this.latinName = latinName;
        }
    }

    private static final List<TickSighting> ALL_SIGHTINGS = new ArrayList<>();
    // Used for duplicate detection
    private static final Map<String, TickSighting> SIGHTING_INDEX = new HashMap<>();

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;
    private static final ZoneId ZONE = ZoneId.of("Europe/London");

    

    public static void main(String[] args) throws Exception {
        loadFromCsv();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/sightings", new SightingsHandler());
        server.createContext("/regions", new RegionsHandler());
        server.createContext("/trends", new TrendsHandler());
        server.createContext("/species", new SpeciesHandler());
        server.createContext("/hotspots", new HotspotsHandler());
        server.createContext("/forecast", new ForecastHandler());
        server.setExecutor(null); // default executor
        System.out.println("TickServer started on http://localhost:8080");
        server.start();
    }

    

    private static void loadFromCsv() {
        ALL_SIGHTINGS.clear();
        SIGHTING_INDEX.clear();

        int skippedMissingCritical = 0;
        int skippedBadDate = 0;
        int skippedDuplicate = 0;

        System.out.println("Loading data from: " + CSV_PATH);

        try (BufferedReader reader = Files.newBufferedReader(
                Paths.get(CSV_PATH), StandardCharsets.UTF_8)) {

            String header = reader.readLine(); // skip header row
            if (header == null) {
                System.out.println("CSV is empty.");
                return;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                // Expected columns: id,date,location,species,latinName
                String[] parts = splitCsvLine(line);
                if (parts.length < 5) {
                    // Incomplete row; skip quietly
                    continue;
                }

                String id = parts[0].trim();
                String dateStr = parts[1].trim();
                String rawLocation = parts[2];
                String species = parts[3].trim();
                String latin = parts[4].trim();

                // Critical fields: id + date
                if (id.isEmpty() || dateStr.isEmpty()) {
                    skippedMissingCritical++;
                    System.err.println("Skipping row (missing ID or date): " + line);
                    continue;
                }

                // Duplicate detection by ID
                if (SIGHTING_INDEX.containsKey(id)) {
                    skippedDuplicate++;
                    System.err.println("Skipping duplicate ID: " + id);
                    continue;
                }

                LocalDateTime timestamp;
                try {
                    timestamp = LocalDateTime.parse(dateStr, ISO_DATE_TIME);
                } catch (Exception e) {
                    skippedBadDate++;
                    System.err.println("Skipping row with invalid date '" + dateStr + "' for ID " + id);
                    continue;
                }

                String location = normaliseLocation(rawLocation);

                TickSighting sighting = new TickSighting(id, timestamp, location, species, latin);
                SIGHTING_INDEX.put(id, sighting);
                ALL_SIGHTINGS.add(sighting);
            }

            System.out.println("CSV load complete.");
            System.out.println("  Skipped (missing critical fields): " + skippedMissingCritical);
            System.out.println("  Skipped (invalid dates):           " + skippedBadDate);
            System.out.println("  Skipped (duplicates):              " + skippedDuplicate);
            System.out.println("  Loaded sightings:                  " + ALL_SIGHTINGS.size());

        } catch (IOException e) {
            System.err.println("FATAL: Could not read CSV file at " + CSV_PATH);
            e.printStackTrace();
        }
    }

    // Very small CSV splitter (does not handle quoted commas, fine for this dataset)
    private static String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private static String normaliseLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        return raw.trim();
    }



    static class SightingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is allowed on this endpoint.");
                    return;
                }

                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));

                if (from == null || to == null) {
                    sendError(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendError(exchange, 400, "'to' must not be before 'from'.");
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
                sendError(exchange, 500, "Unexpected server error.");
            }
        }
    }

 
    static class RegionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is allowed on this endpoint.");
                    return;
                }

                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));

                if (from == null || to == null) {
                    sendError(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendError(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }

                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                Map<String, Integer> counts = new HashMap<>();
                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        String region = normaliseLocation(s.location);
                        counts.put(region, counts.getOrDefault(region, 0) + 1);
                    }
                }

                List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
                entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // descending by count

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
                sendError(exchange, 500, "Unexpected server error.");
            }
        }
    }


    static class TrendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is allowed on this endpoint.");
                    return;
                }

                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));
                String granularity = params.getOrDefault("granularity", "monthly")
                        .toLowerCase(Locale.ROOT);

                if (from == null || to == null) {
                    sendError(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendError(exchange, 400, "'to' must not be before 'from'.");
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
                sendError(exchange, 500, "Unexpected server error.");
            }
        }
    }

    static class SpeciesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is allowed on this endpoint.");
                    return;
                }

                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));
                String location = params.get("location");

                if (from == null || to == null) {
                    sendError(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendError(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }

                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                Map<String, Integer> counts = new HashMap<>();
                Map<String, String> latinBySpecies = new HashMap<>();

                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        if (location == null || location.isBlank()
                                || s.location.equalsIgnoreCase(location)) {
                            String key = s.species;
                            counts.put(key, counts.getOrDefault(key, 0) + 1);
                            latinBySpecies.putIfAbsent(key, s.latinName);
                        }
                    }
                }

                List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
                entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // most common first

                StringBuilder json = new StringBuilder();
                json.append("[");
                for (int i = 0; i < entries.size(); i++) {
                    Map.Entry<String, Integer> e = entries.get(i);
                    if (i > 0) json.append(",");
                    String species = e.getKey();
                    String latin = latinBySpecies.getOrDefault(species, "");
                    json.append("{")
                            .append("\"species\":\"").append(escapeJson(species)).append("\",")
                            .append("\"latinName\":\"").append(escapeJson(latin)).append("\",")
                            .append("\"count\":").append(e.getValue())
                            .append("}");
                }
                json.append("]");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Unexpected server error.");
            }
        }
    }

    static class HotspotsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is allowed on this endpoint.");
                    return;
                }

                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));

                if (from == null || to == null) {
                    sendError(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendError(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }

                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                Map<String, Integer> counts = new HashMap<>();
                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        String region = normaliseLocation(s.location);
                        counts.put(region, counts.getOrDefault(region, 0) + 1);
                    }
                }

                int maxCount = 0;
                for (int c : counts.values()) {
                    if (c > maxCount) maxCount = c;
                }
                double max = maxCount == 0 ? 1.0 : maxCount;

                List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
                entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // most intense first

                StringBuilder json = new StringBuilder();
                json.append("[");
                for (int i = 0; i < entries.size(); i++) {
                    Map.Entry<String, Integer> e = entries.get(i);
                    if (i > 0) json.append(",");
                    double intensity = e.getValue() / max; // 0.0 - 1.0
                    json.append("{")
                            .append("\"region\":\"").append(escapeJson(e.getKey())).append("\",")
                            .append("\"count\":").append(e.getValue()).append(",")
                            .append("\"intensity\":").append(String.format(Locale.ROOT, "%.3f", intensity))
                            .append("}");
                }
                json.append("]");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Unexpected server error.");
            }
        }
    }

    
    static class ForecastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is allowed on this endpoint.");
                    return;
                }

                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                LocalDate from = parseDate(params.get("from"));
                LocalDate to = parseDate(params.get("to"));
                String monthsAheadStr = params.getOrDefault("monthsAhead", "3");

                int monthsAhead;
                try {
                    monthsAhead = Integer.parseInt(monthsAheadStr);
                    if (monthsAhead < 1) monthsAhead = 1;
                    if (monthsAhead > 12) monthsAhead = 12;
                } catch (NumberFormatException ex) {
                    monthsAhead = 3;
                }

                if (from == null || to == null) {
                    sendError(exchange, 400, "Parameters 'from' and 'to' (YYYY-MM-DD) are required.");
                    return;
                }
                if (to.isBefore(from)) {
                    sendError(exchange, 400, "'to' must not be before 'from'.");
                    return;
                }

                LocalDateTime fromDt = from.atStartOfDay();
                LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

                // Build monthly counts
                Map<YearMonth, Integer> counts = new HashMap<>();
                for (TickSighting s : ALL_SIGHTINGS) {
                    if (!s.timestamp.isBefore(fromDt) && !s.timestamp.isAfter(toDt)) {
                        YearMonth ym = YearMonth.from(s.timestamp.atZone(ZONE));
                        counts.put(ym, counts.getOrDefault(ym, 0) + 1);
                    }
                }

                List<YearMonth> months = new ArrayList<>(counts.keySet());
                months.sort(Comparator.naturalOrder());

                if (months.size() < 2) {
                    sendError(exchange, 400, "Not enough data to generate a forecast (need at least 2 months).");
                    return;
                }

                int n = months.size();
              
                double sumX = 0.0;
                double sumY = 0.0;
                double sumXY = 0.0;
                double sumX2 = 0.0;

                for (int i = 0; i < n; i++) {
                    double x = i;
                    double y = counts.get(months.get(i));
                    sumX += x;
                    sumY += y;
                    sumXY += x * y;
                    sumX2 += x * x;
                }

                double meanX = sumX / n;
                double meanY = sumY / n;
                double denom = sumX2 - sumX * meanX;
                double slope = (denom == 0.0) ? 0.0 : (sumXY - sumX * meanY) / denom;
                double intercept = meanY - slope * meanX;

                YearMonth lastMonth = months.get(n - 1);

                StringBuilder json = new StringBuilder();
                json.append("[");

                for (int k = 1; k <= monthsAhead; k++) {
                    if (k > 1) json.append(",");
                    int futureIndex = n - 1 + k;
                    YearMonth futureMonth = lastMonth.plusMonths(k);
                    double predicted = intercept + slope * futureIndex;
                    if (predicted < 0) predicted = 0;
                    long rounded = Math.round(predicted);

                    json.append("{")
                            .append("\"periodLabel\":\"").append(futureMonth.toString()).append("\",")
                            .append("\"predictedCount\":").append(rounded)
                            .append("}");
                }

                json.append("]");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Unexpected server error.");
            }
        }
    }



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
            YearWeek(int year, int week) {
                this.year = year;
                this.week = week;
            }
            @Override
            public boolean equals(Object o) {
                if (!(o instanceof YearWeek)) return false;
                YearWeek other = (YearWeek) o;
                return year == other.year && week == other.week;
            }
            @Override
            public int hashCode() {
                return year * 100 + week;
            }
        }

        WeekFields wf = WeekFields.of(Locale.UK);
        Map<YearWeek, Integer> counts = new HashMap<>();
        for (TickSighting s : sightings) {
            int year = s.timestamp.get(wf.weekBasedYear());
            int week = s.timestamp.get(wf.weekOfWeekBasedYear());
            YearWeek yw = new YearWeek(year, week);
            counts.put(yw, counts.getOrDefault(yw, 0) + 1);
        }

        List<YearWeek> weeks = new ArrayList<>(counts.keySet());
        weeks.sort((a, b) -> {
            int cmp = Integer.compare(a.year, b.year);
            if (cmp != 0) return cmp;
            return Integer.compare(a.week, b.week);
        });

        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < weeks.size(); i++) {
            YearWeek yw = weeks.get(i);
            if (i > 0) json.append(",");
            json.append("{")
                    .append("\"periodLabel\":\"").append(yw.year)
                    .append("-W").append(yw.week).append("\",")
                    .append("\"count\":").append(counts.get(yw))
                    .append("}");
        }
        json.append("]");
        return json.toString();
    }

   

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

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"status\":").append(status).append(",");
        json.append("\"error\":\"").append(escapeJson(message)).append("\"");
        json.append("}");
        sendJson(exchange, status, json.toString());
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
