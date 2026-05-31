package safepath;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class SafePathServer {
    private static final int PORT = 8080;
    private static Graph graph;
    private static List<KMeansClustering.Cluster> hotspots;
    private static SafetyScorer safetyScorer;
    private static List<CrimeDataGenerator.CrimeEvent> crimeEvents;

    // Default coordinates: New Delhi Center
    private static final double CENTER_LAT = 28.6139;
    private static final double CENTER_LON = 77.2090;

    public static void main(String[] args) throws IOException {
        System.out.println("Initializing SafePath backend engine...");
        
        // 1. Generate road network (15x15 grid of coordinates spanning ~3km)
        graph = Graph.generateProceduralGraph(CENTER_LAT, CENTER_LON, 15, 200.0);
        System.out.println("Procedural graph generated with " + graph.nodes.size() + " intersections.");

        // 2. Generate simulated crime data
        crimeEvents = CrimeDataGenerator.generateSimulatedCrimes(CENTER_LAT, CENTER_LON, 350);
        System.out.println("Generated " + crimeEvents.size() + " crime historical events.");

        // 3. Cluster crime points with K-Means++ (K=5 hotspots)
        hotspots = KMeansClustering.clusterCrimes(crimeEvents, 5, 100);
        System.out.println("K-Means++ clustering complete. " + hotspots.size() + " hotspots detected.");
        for (int i = 0; i < hotspots.size(); i++) {
            KMeansClustering.Cluster h = hotspots.get(i);
            System.out.printf(Locale.US, "Hotspot %d: Center(%.4f, %.4f), Radius: %.1fm, Intensity: %.1f\n", 
                i+1, h.lat, h.lon, h.radiusMeters, h.intensity);
        }

        // 4. Initialize Safety Scorer
        safetyScorer = new SafetyScorer(hotspots);

        // 5. Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Contexts
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/hotspots", new HotspotsHandler());
        server.createContext("/api/route", new RouteHandler());
        server.createContext("/api/uv", new UVHandler());
        server.createContext("/api/emergency", new EmergencyHandler());
        server.createContext("/api/safety-score", new SafetyScoreHandler());

        server.setExecutor(null); // default executor
        server.start();
        System.out.println("SafePath server successfully started at http://localhost:" + PORT);
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                String value = (idx > 0 && pair.length() > idx + 1) 
                    ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : "";
                params.put(key, value);
            } catch (Exception e) {
                // Ignore decoding errors
            }
        }
        return params;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // Serving index.html, style.css, app.js, etc.
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Target relative path under "public" folder
            File file = new File("public" + path);
            if (!file.exists() || file.isDirectory()) {
                // Fallback to 404 response
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                OutputStream os = exchange.getResponseBody();
                os.write(notFound.getBytes());
                os.close();
                return;
            }

            String contentType = "text/html";
            if (path.endsWith(".css")) {
                contentType = "text/css";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (path.endsWith(".ico")) {
                contentType = "image/x-icon";
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());
            
            try (InputStream is = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }
    }

    // Return crime hotspots and crimes for heatmapping
    static class HotspotsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"center\": {\"lat\": ").append(CENTER_LAT).append(", \"lon\": ").append(CENTER_LON).append("},");
            
            // Raw crimes
            sb.append("\"crimes\": [");
            for (int i = 0; i < crimeEvents.size(); i++) {
                CrimeDataGenerator.CrimeEvent c = crimeEvents.get(i);
                sb.append(String.format(Locale.US, "{\"lat\": %.6f, \"lon\": %.6f, \"type\": \"%s\", \"severity\": %d, \"time\": \"%s\"}",
                    c.lat, c.lon, c.type, c.severity, c.timeOfDay));
                if (i < crimeEvents.size() - 1) sb.append(",");
            }
            sb.append("],");

            // Clusters
            sb.append("\"hotspots\": [");
            for (int i = 0; i < hotspots.size(); i++) {
                KMeansClustering.Cluster h = hotspots.get(i);
                sb.append(String.format(Locale.US, "{\"lat\": %.6f, \"lon\": %.6f, \"radius\": %.2f, \"intensity\": %.2f, \"crimeCount\": %d}",
                    h.lat, h.lon, h.radiusMeters, h.intensity, h.crimeCount));
                if (i < hotspots.size() - 1) sb.append(",");
            }
            sb.append("]");
            sb.append("}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // Handles pathfinding routing request
    static class RouteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            double startLat = Double.parseDouble(queryParams.getOrDefault("startLat", String.valueOf(CENTER_LAT)));
            double startLon = Double.parseDouble(queryParams.getOrDefault("startLon", String.valueOf(CENTER_LON)));
            double endLat = Double.parseDouble(queryParams.getOrDefault("endLat", String.valueOf(CENTER_LAT)));
            double endLon = Double.parseDouble(queryParams.getOrDefault("endLon", String.valueOf(CENTER_LON)));
            String timeOfDay = queryParams.getOrDefault("timeOfDay", "Night");

            // Find nearest node IDs in our network
            Graph.Node startNode = graph.findNearestNode(startLat, startLon);
            Graph.Node endNode = graph.findNearestNode(endLat, endLon);

            System.out.println("\n------------------------------------------------");
            System.out.printf(Locale.US, "[Java Backend] Route requested from (%.4f, %.4f) to (%.4f, %.4f) at %s\n", 
                startLat, startLon, endLat, endLon, timeOfDay);
            System.out.printf(Locale.US, "[Java Backend] Mapped to nearest street intersections: %s ➔ %s\n", 
                startNode.id, endNode.id);

            // Compute Dijkstra path
            System.out.println("[Java Backend] Running Dijkstra Shortest Path Algorithm...");
            DijkstraAlgorithm.PathResult fastestResult = DijkstraAlgorithm.computeShortestPath(graph, startNode.id, endNode.id);
            System.out.printf(Locale.US, "[Java Backend] Dijkstra Complete: Shortest Path = %.1f meters (%d intersections)\n", 
                fastestResult.totalDistance, fastestResult.path.size());
            
            // Compute A* path
            System.out.println("[Java Backend] Running A* Safest Path Algorithm (using K-Means hotspots)...");
            AStarAlgorithm.PathResult safestResult = AStarAlgorithm.computeSafestPath(graph, startNode.id, endNode.id, safetyScorer, timeOfDay);
            System.out.printf(Locale.US, "[Java Backend] A* Complete: Safest Path = %.1f meters (%d intersections), Safety Score = %.1f%%\n", 
                safestResult.totalDistance, safestResult.path.size(), safestResult.averageSafetyScore);
            System.out.println("------------------------------------------------");

            // Build JSON output
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            
            // Fastest
            sb.append("\"fastest\": {");
            sb.append("\"distance\": ").append(fastestResult.totalDistance).append(",");
            sb.append("\"durationMin\": ").append(Math.round(fastestResult.totalDistance / 1.4 / 60)).append(","); // Walking speed: 1.4 m/s
            
            // Compute safety score along fastest path
            double fastestSafety = 100.0;
            if (!fastestResult.path.isEmpty()) {
                double safetySum = 0;
                for (int i = 0; i < fastestResult.path.size() - 1; i++) {
                    double midLat = (fastestResult.path.get(i).lat + fastestResult.path.get(i+1).lat) / 2.0;
                    double midLon = (fastestResult.path.get(i).lon + fastestResult.path.get(i+1).lon) / 2.0;
                    safetySum += safetyScorer.computeSafetyScore(midLat, midLon, timeOfDay);
                }
                fastestSafety = fastestResult.path.size() > 1 ? (safetySum / (fastestResult.path.size() - 1)) : 100.0;
            }
            sb.append("\"safetyScore\": ").append(fastestSafety).append(",");
            sb.append("\"path\": [");
            for (int i = 0; i < fastestResult.path.size(); i++) {
                Graph.Node node = fastestResult.path.get(i);
                sb.append(String.format(Locale.US, "{\"lat\": %.6f, \"lon\": %.6f}", node.lat, node.lon));
                if (i < fastestResult.path.size() - 1) sb.append(",");
            }
            sb.append("]");
            sb.append("},");

            // Safest
            sb.append("\"safest\": {");
            sb.append("\"distance\": ").append(safestResult.totalDistance).append(",");
            sb.append("\"durationMin\": ").append(Math.round(safestResult.totalDistance / 1.4 / 60)).append(",");
            sb.append("\"safetyScore\": ").append(safestResult.averageSafetyScore).append(",");
            sb.append("\"path\": [");
            for (int i = 0; i < safestResult.path.size(); i++) {
                Graph.Node node = safestResult.path.get(i);
                sb.append(String.format(Locale.US, "{\"lat\": %.6f, \"lon\": %.6f}", node.lat, node.lon));
                if (i < safestResult.path.size() - 1) sb.append(",");
            }
            sb.append("]");
            sb.append("}");

            sb.append("}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // UV Index request handler
    static class UVHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            double lat = Double.parseDouble(queryParams.getOrDefault("lat", String.valueOf(CENTER_LAT)));
            double lon = Double.parseDouble(queryParams.getOrDefault("lon", String.valueOf(CENTER_LON)));

            UVIndexService.UVResponse uv = UVIndexService.getUVIndex(lat, lon);

            String json = String.format(Locale.US, "{\"uvIndex\": %.2f, \"riskCategory\": \"%s\", \"recommendation\": \"%s\"}",
                uv.uvIndex, uv.riskCategory, uv.recommendation);

            sendJsonResponse(exchange, 200, json);
        }
    }

    // Emergency Locations request handler
    static class EmergencyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            double lat = Double.parseDouble(queryParams.getOrDefault("lat", String.valueOf(CENTER_LAT)));
            double lon = Double.parseDouble(queryParams.getOrDefault("lon", String.valueOf(CENTER_LON)));

            List<EmergencyService.EmergencyLocation> locations = EmergencyService.getNearbyEmergencyLocations(lat, lon);

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < locations.size(); i++) {
                EmergencyService.EmergencyLocation loc = locations.get(i);
                sb.append(String.format(Locale.US, "{\"name\": \"%s\", \"type\": \"%s\", \"lat\": %.6f, \"lon\": %.6f, \"distanceMeters\": %.1f, \"contact\": \"%s\"}",
                    loc.name, loc.type, loc.lat, loc.lon, loc.distanceMeters, loc.contact));
                if (i < locations.size() - 1) sb.append(",");
            }
            sb.append("]");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // Live safety score computation request handler
    static class SafetyScoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            double lat = Double.parseDouble(queryParams.getOrDefault("lat", String.valueOf(CENTER_LAT)));
            double lon = Double.parseDouble(queryParams.getOrDefault("lon", String.valueOf(CENTER_LON)));
            String timeOfDay = queryParams.getOrDefault("timeOfDay", "Night");

            double score = safetyScorer.computeSafetyScore(lat, lon, timeOfDay);

            String json = String.format(Locale.US, "{\"safetyScore\": %.2f, \"timeOfDay\": \"%s\"}", score, timeOfDay);

            sendJsonResponse(exchange, 200, json);
        }
    }
}
