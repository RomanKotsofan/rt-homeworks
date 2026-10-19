package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }


    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {

                // Asteptam un mesaj MQTT
                String message = inbox.take();

                // Salvam mesajul original in CSV
                storage.append(kind, message);

                try {
                    // Separarea campurilor mesajului
                    String[] parts = message.split(
                            ";", kind.equals("status") ? 3 : -1
                    );

                    if (parts.length < 3 || parts[0].isBlank()) {
                        throw new IllegalArgumentException("Format invalid");
                    }

                    String deviceId = parts[0];
                    long timestamp = Long.parseLong(parts[1]);

                    switch (kind) {

                        case "telemetry":
                            if (parts.length != 3) {
                                throw new IllegalArgumentException("Telemetrie invalida");
                            }

                            double temperature = Double.parseDouble(parts[2]);

                            if (!Double.isFinite(temperature)) {
                                throw new IllegalArgumentException("Temperatura invalida");
                            }

                            dashboard.updateTelemetry(
                                    deviceId, timestamp, temperature
                            );
                            break;

                        case "alarm":
                            if (parts.length != 4) {
                                throw new IllegalArgumentException("Alarma invalida");
                            }

                            double alarmTemp = Double.parseDouble(parts[2]);
                            double threshold = Double.parseDouble(parts[3]);

                            if (!Double.isFinite(alarmTemp) ||
                                    !Double.isFinite(threshold)) {
                                throw new IllegalArgumentException("Valori invalide");
                            }

                            dashboard.updateAlarm(deviceId, timestamp);

                            System.out.println("[ALARM] " + message);
                            break;

                        case "status":
                            if (parts.length != 3 || parts[2].isBlank()) {
                                throw new IllegalArgumentException("Status invalid");
                            }

                            dashboard.updateStatus(deviceId, timestamp);

                            System.out.println("[STATUS] " + message);
                            break;

                        default:
                            throw new IllegalArgumentException("Tip necunoscut");
                    }

                } catch (IllegalArgumentException e) {
                    System.out.println(
                            "[ERROR] Mesaj invalid: " + message
                                    + " (" + e.getMessage() + ")"
                    );
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
