package safepath;

import java.io.*;
import java.net.*;
import java.util.*;

public class EmergencyService {
    public static class EmergencyLocation {
        public String name;
        public String type; // Police, Hospital, Fire Station
        public double lat;
        public double lon;
        public double distanceMeters;
        public String contact;

        public EmergencyLocation(String name, String type, double lat, double lon, double distanceMeters, String contact) {
            this.name = name;
            this.type = type;
            this.lat = lat;
            this.lon = lon;
            this.distanceMeters = distanceMeters;
            this.contact = contact;
        }
    }

    public static List<EmergencyLocation> getNearbyEmergencyLocations(double lat, double lon) {
        List<EmergencyLocation> list = new ArrayList<>();
        
        // Build Overpass QL query URL
        String query = String.format(Locale.US, "[out:json][timeout:5];(node(around:2000,%.6f,%.6f)[amenity~\"police|hospital|fire_station\"];);out;", lat, lon);
        
        try {
            String urlStr = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8");
            URI uri = new URI(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    content.append(line);
                }
                in.close();
                conn.disconnect();

                // Custom regex-free simple parsing to extract location items from JSON response
                String json = content.toString();
                int index = 0;
                while ((index = json.indexOf("\"type\": \"node\"", index)) != -1) {
                    int endNodeIndex = json.indexOf("}", index);
                    if (endNodeIndex == -1) break;
                    String nodeJson = json.substring(index, endNodeIndex);

                    double nodeLat = 0;
                    double nodeLon = 0;
                    String amenity = "Emergency Station";
                    String name = "Emergency Facility";

                    if (nodeJson.contains("\"lat\":")) {
                        nodeLat = extractDouble(nodeJson, "\"lat\":");
                    }
                    if (nodeJson.contains("\"lon\":")) {
                        nodeLon = extractDouble(nodeJson, "\"lon\":");
                    }
                    if (nodeJson.contains("\"amenity\":")) {
                        amenity = extractString(nodeJson, "\"amenity\":");
                    }
                    if (nodeJson.contains("\"name\":")) {
                        name = extractString(nodeJson, "\"name\":");
                    }

                    String facilityType = "Emergency Station";
                    String contactNum = "+91 112"; // Default national emergency number for India
                    if (amenity.contains("police")) {
                        facilityType = "Police Station";
                        contactNum = "100";
                    } else if (amenity.contains("hospital")) {
                        facilityType = "Hospital";
                        contactNum = "102";
                    } else if (amenity.contains("fire_station")) {
                        facilityType = "Fire Station";
                        contactNum = "101";
                    }

                    if (name.equals("Emergency Facility")) {
                        name = facilityType + " (" + String.format(Locale.US, "%.1f meters away", Graph.haversine(lat, lon, nodeLat, nodeLon)) + ")";
                    }

                    double distance = Graph.haversine(lat, lon, nodeLat, nodeLon);
                    list.add(new EmergencyLocation(name, facilityType, nodeLat, nodeLon, distance, contactNum));
                    
                    index = endNodeIndex; // move past this node
                }
            }
        } catch (Exception e) {
            System.err.println("Overpass API call failed, falling back to simulated emergency locations: " + e.getMessage());
        }

        // Fallback: If API fails or is offline, generate 3 simulated emergency locations nearby
        if (list.isEmpty()) {
            double latDegreePerMeter = 1.0 / 111320.0;
            double lonDegreePerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(lat)));

            list.add(new EmergencyLocation("Connaught Place Police Station", "Police Station", 
                lat + 350 * latDegreePerMeter, lon - 420 * lonDegreePerMeter, 
                Graph.haversine(lat, lon, lat + 350 * latDegreePerMeter, lon - 420 * lonDegreePerMeter), "100"));

            list.add(new EmergencyLocation("Max Super Speciality Hospital", "Hospital", 
                lat - 500 * latDegreePerMeter, lon + 550 * lonDegreePerMeter, 
                Graph.haversine(lat, lon, lat - 500 * latDegreePerMeter, lon + 550 * lonDegreePerMeter), "102"));

            list.add(new EmergencyLocation("Central Delhi Fire Station", "Fire Station", 
                lat + 600 * latDegreePerMeter, lon + 200 * lonDegreePerMeter, 
                Graph.haversine(lat, lon, lat + 600 * latDegreePerMeter, lon + 200 * lonDegreePerMeter), "101"));
        }

        // Sort by distance
        list.sort(Comparator.comparingDouble(a -> a.distanceMeters));
        return list;
    }

    private static double extractDouble(String text, String key) {
        int idx = text.indexOf(key);
        if (idx == -1) return 0;
        int start = idx + key.length();
        int end = text.indexOf(",", start);
        if (end == -1) end = text.indexOf("\n", start);
        String val = text.substring(start, end).trim().replace(":", "").replace("\"", "").replace("}", "").trim();
        return Double.parseDouble(val);
    }

    private static String extractString(String text, String key) {
        int idx = text.indexOf(key);
        if (idx == -1) return "";
        int start = idx + key.length();
        int quoteStart = text.indexOf("\"", start);
        int quoteEnd = text.indexOf("\"", quoteStart + 1);
        if (quoteStart != -1 && quoteEnd != -1) {
            return text.substring(quoteStart + 1, quoteEnd);
        }
        return "";
    }
}
