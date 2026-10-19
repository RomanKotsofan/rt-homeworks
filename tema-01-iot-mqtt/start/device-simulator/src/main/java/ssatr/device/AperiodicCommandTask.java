package ssatr.device;

import java.util.concurrent.BlockingQueue;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }


    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {

                // Asteptam o comanda din coada
                String command = commands.take().trim();
                String[] parts = command.split("\\s+");

                String response;

                try {
                    switch (parts[0].toUpperCase()) {

                        case "SET_INTERVAL":
                            if (parts.length != 2) {
                                throw new IllegalArgumentException("Interval invalid");
                            }

                            long interval = Long.parseLong(parts[1]);

                            if (interval <= 0) {
                                throw new IllegalArgumentException("Intervalul trebuie sa fie pozitiv");
                            }

                            periodic.setPeriodMs(interval);
                            response = "OK SET_INTERVAL " + interval;
                            break;

                        case "SET_THRESHOLD":
                            if (parts.length != 2) {
                                throw new IllegalArgumentException("Prag invalid");
                            }

                            double threshold = Double.parseDouble(parts[1]);

                            if (!Double.isFinite(threshold)) {
                                throw new IllegalArgumentException("Prag invalid");
                            }

                            periodic.setThreshold(threshold);
                            response = "OK SET_THRESHOLD " + threshold;
                            break;

                        case "RESET":
                            if (parts.length != 1) {
                                throw new IllegalArgumentException("RESET fara parametri");
                            }

                            sensor.reset();
                            response = "OK RESET";
                            break;

                        case "STATUS":
                            if (parts.length != 1) {
                                throw new IllegalArgumentException("STATUS fara parametri");
                            }

                            response = String.format(
                                    java.util.Locale.US,
                                    "OK STATUS interval=%d threshold=%.2f",
                                    periodic.getPeriodMs(),
                                    periodic.getThreshold()
                            );
                            break;

                        default:
                            response = "ERROR Comanda necunoscuta";
                    }

                } catch (IllegalArgumentException e) {
                    response = "ERROR " + e.getMessage();
                }

                // Publicam confirmarea prin MQTT
                String message = deviceId + ";" +
                        System.currentTimeMillis() + ";" + response;

                mqtt.publish(Config.statusTopic(deviceId), message);

                System.out.println("[APERIODIC] " + message);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
