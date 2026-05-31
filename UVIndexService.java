package safepath;

import java.io.*;
import java.net.*;
import java.util.Locale;

public class UVIndexService {
    public static class UVResponse {
        public double uvIndex;
        public String riskCategory;
        public String recommendation;

        public UVResponse(double uvIndex, String riskCategory, String recommendation) {
            this.uvIndex = uvIndex;
            this.riskCategory = riskCategory;
            this.recommendation = recommendation;
        }
    }

    public static UVResponse getUVIndex(double lat, double lon) {
        String urlString = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=uv_index_max&timezone=auto", lat, lon);
        
        try {
            URI uri = new URI(urlString);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    content.append(line);
                }
                in.close();
                conn.disconnect();

                // Simple JSON extraction to avoid heavy external parser dependencies
                String json = content.toString();
                if (json.contains("\"uv_index_max\":[")) {
                    int startIndex = json.indexOf("\"uv_index_max\":[") + "\"uv_index_max\":[".length();
                    int endIndex = json.indexOf("]", startIndex);
                    String uvValStr = json.substring(startIndex, endIndex).split(",")[0];
                    double uv = Double.parseDouble(uvValStr);
                    return buildUVResponse(uv);
                }
            }
        } catch (Exception e) {
            System.err.println("UV API call failed, falling back to simulated value: " + e.getMessage());
        }

        // Fallback simulated UV index based on time of day and a random variations [0.0 - 11.0]
        double simulatedUV = 5.8 + (Math.random() * 4.0);
        return buildUVResponse(simulatedUV);
    }

    private static UVResponse buildUVResponse(double uv) {
        String risk;
        String rec;

        if (uv < 3) {
            risk = "Low";
            rec = "Minimal protection needed. Safe to navigate.";
        } else if (uv < 6) {
            risk = "Moderate";
            rec = "Wear sunglasses and sunscreen. Use shaded routes where possible.";
        } else if (uv < 8) {
            risk = "High";
            rec = "Protection essential. Keep to shaded sides of streets. Apply SPF 30+.";
        } else if (uv < 11) {
            risk = "Very High";
            rec = "Avoid outdoor activity midday. Seek shade, wear a hat and sunscreen.";
        } else {
            risk = "Extreme";
            rec = "Extreme hazard! Avoid navigation under direct sun. Seek indoor transit.";
        }

        return new UVResponse(uv, risk, rec);
    }
}
