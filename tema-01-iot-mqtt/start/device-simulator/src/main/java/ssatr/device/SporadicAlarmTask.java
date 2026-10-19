
package ssatr.device;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TASK SPORADIC: trateaza alarmele de temperatura.
 * Interval minim intre alarme: 5000 ms.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;
    private final PeriodicTelemetryTask periodic;

    public SporadicAlarmTask(
            String deviceId,
            MqttConnection mqtt,
            BlockingQueue<Double> alarmEvents,
            PeriodicTelemetryTask periodic) {

        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        long lastAlarmTime = 0;
        boolean firstAlarm = true;

        try {
            while (!Thread.currentThread().isInterrupted()) {

                // Asteptam un eveniment din coada
                double temperature = alarmEvents.take();

                long now = System.nanoTime();

                // Verificam intervalul minim dintre alarme
                if (!firstAlarm &&
                        now - lastAlarmTime <
                                TimeUnit.MILLISECONDS.toNanos(MIN_INTERARRIVAL_MS)) {

                    System.out.println(
                            "[SPORADIC] Eveniment respins: interval prea scurt"
                    );
                    continue;
                }

                firstAlarm = false;
                lastAlarmTime = now;

                // Citim pragul actual din taskul periodic
                double threshold = periodic.getThreshold();

                // Construim mesajul MQTT
                long timestamp = System.currentTimeMillis();

                String message = String.format(
                        Locale.US,
                        "%s;%d;%.2f;%.2f",
                        deviceId,
                        timestamp,
                        temperature,
                        threshold
                );

                // Publicam alarma
                mqtt.publish(Config.alarmTopic(deviceId), message);

                System.out.println("[SPORADIC] " + message);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

