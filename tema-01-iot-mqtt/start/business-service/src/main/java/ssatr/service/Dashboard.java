package ssatr.service;

/**
 * Ultima stare cunoscută a fiecărui device: ultima temperatură, momentul
 * ultimului mesaj, numărul de alarme etc.
 *
 * Este actualizată de thread-urile MessageProcessor și citită de CommandConsole,
 * deci accesul trebuie sincronizat.
 */
public class Dashboard {


    private final java.util.Map<String, DeviceState> devices =
            new java.util.LinkedHashMap<>();

    private static class DeviceState {
        Double temperature;
        long lastMessageTimestamp;
        int alarmCount;
    }

    private DeviceState getState(String deviceId) {
        return devices.computeIfAbsent(deviceId, id -> new DeviceState());
    }

    public synchronized void updateTelemetry(
            String deviceId, long timestamp, double temperature) {

        DeviceState state = getState(deviceId);
        state.temperature = temperature;
        state.lastMessageTimestamp = timestamp;
    }

    public synchronized void updateAlarm(
            String deviceId, long timestamp) {

        DeviceState state = getState(deviceId);
        state.alarmCount++;
        state.lastMessageTimestamp = timestamp;
    }

    public synchronized void updateStatus(
            String deviceId, long timestamp) {

        getState(deviceId).lastMessageTimestamp = timestamp;
    }

    //   (de exemplu un Map<String, ...> cu cheia deviceId)
    //   și adăugați metodele de actualizare apelate din MessageProcessor.

    /** Afișează un tabel cu starea tuturor device-urilor. */

    public synchronized void print() {

        System.out.printf(
                "%-15s %-12s %-20s %s%n",
                "DEVICE", "TEMP (C)", "ULTIMUL MESAJ", "ALARME"
        );

        if (devices.isEmpty()) {
            System.out.println("Nu au fost primite mesaje inca.");
            return;
        }

        long now = System.currentTimeMillis();

        for (java.util.Map.Entry<String, DeviceState> entry
                : devices.entrySet()) {

            DeviceState state = entry.getValue();

            String temp = state.temperature == null
                    ? "-"
                    : String.format(java.util.Locale.US,
                    "%.2f", state.temperature);

            long ageSeconds = Math.max(
                    0L,
                    (now - state.lastMessageTimestamp) / 1000L
            );

            System.out.printf(
                    "%-15s %-12s %-20s %d%n",
                    entry.getKey(),
                    temp,
                    "acum " + ageSeconds + " s",
                    state.alarmCount
            );
        }
    }

}
