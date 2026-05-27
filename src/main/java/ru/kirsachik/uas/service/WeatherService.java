package ru.kirsachik.uas.service;

import org.springframework.stereotype.Service;

@Service
public class WeatherService {

    /**
     * Размер ячейки демонстрационной погодной карты.
     * Значение совпадает с WEATHER_GRID_DEGREES во frontend-коде.
     */
    private static final double WEATHER_GRID_DEGREES = 0.012;

    public WeatherSnapshot current(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return snapshotForCode("CLEAR");
        }

        int latCell = (int) Math.floor(latitude / WEATHER_GRID_DEGREES);
        int lonCell = (int) Math.floor(longitude / WEATHER_GRID_DEGREES);
        return snapshotForCode(weatherCodeForCell(latCell, lonCell));
    }

    private static String weatherCodeForCell(int latCell, int lonCell) {
        double value = weatherHash(latCell, lonCell);
        if (value < 0.50) return "CLEAR";
        if (value < 0.66) return "RAIN";
        if (value < 0.81) return "WIND";
        if (value < 0.93) return "FOG";
        return "STORM";
    }

    private static double weatherHash(int latCell, int lonCell) {
        double raw = Math.sin(latCell * 12.9898 + lonCell * 78.233) * 43758.5453;
        return raw - Math.floor(raw);
    }

    private static WeatherSnapshot snapshotForCode(String code) {
        return switch (code) {
            case "WIND" -> new WeatherSnapshot(
                    "WIND", "Сильный ветер", 12.0, 0.0, 8.0,
                    0.78, 0.82, 1.35, 1.55, "WARNING"
            );
            case "RAIN" -> new WeatherSnapshot(
                    "RAIN", "Дождь", 6.5, 3.2, 7.0,
                    0.86, 0.90, 1.25, 1.25, "INFO"
            );
            case "FOG" -> new WeatherSnapshot(
                    "FOG", "Туман", 2.5, 0.2, 1.2,
                    0.70, 0.78, 1.18, 1.10, "WARNING"
            );
            case "STORM" -> new WeatherSnapshot(
                    "STORM", "Гроза", 17.0, 8.0, 3.0,
                    0.52, 0.62, 1.85, 2.10, "CRITICAL"
            );
            default -> new WeatherSnapshot(
                    "CLEAR", "Ясно", 2.0, 0.0, 10.0,
                    1.0, 1.0, 1.0, 1.0, "INFO"
            );
        };
    }
}
