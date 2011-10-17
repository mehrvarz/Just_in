package org.timur.justin;
 
import java.util.List;
 
import android.app.Activity;
import android.util.Config;
import android.util.Log;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
 
public class GyroscopeManager {
 
    private static String LOGTAG = "GyroscopeManager";

    /** Accuracy configuration */
    private static float threshold     = 0.2f;
    private static int interval     = 1000;
 
    private static Sensor sensor;
    private static SensorManager sensorManager;
    // you could use an OrientationListener array instead
    // if you plans to use more than one listener
    private static AccelerometerListener listener;
 
    /** indicates whether or not Accelerometer Sensor is supported */
    private static Boolean supported;
    /** indicates whether or not Accelerometer Sensor is running */
    private static boolean running = false;
 
    /**
     * Returns true if the manager is listening to orientation changes
     */
    public static boolean isListening() {
        return running;
    }
 
    /**
     * Unregisters listeners
     */
    public static void stopListening() {
        running = false;
        try {
            if (sensorManager != null && sensorEventListener != null) {
                sensorManager.unregisterListener(sensorEventListener);
            }
        } catch (Exception e) {}
    }
 
    /**
     * Returns true if at least one Accelerometer sensor is available
     */
    public static boolean isSupported() {
/*
        if (supported == null) {
            if (JustInActivity.context != null) {
                sensorManager = (SensorManager) JustInActivity.context.
                        getSystemService(Context.SENSOR_SERVICE);
                List<Sensor> sensors = sensorManager.getSensorList(
                        Sensor.TYPE_ACCELEROMETER);
                supported = new Boolean(sensors.size() > 0);
            } else {
                supported = Boolean.FALSE;
            }
        }
        return supported;
*/
        return true;
    }
 
    /**
     * Configure the listener for shaking
     * @param threshold
     *             minimum acceleration variation for considering shaking
     * @param interval
     *             minimum interval between to shake events
     */
    public static void configure(float setThreshold, int setInterval) {
        threshold = setThreshold;
        interval = setInterval;
    }
 
    /**
     * Registers a listener and start listening
     * @param accelerometerListener
     *             callback for accelerometer events
     */
    public static void startListening(
            Activity activity, 
            AccelerometerListener accelerometerListener) {
        sensorManager = (SensorManager) activity.
                getSystemService(Context.SENSOR_SERVICE);
/*
        List<Sensor> sensors = sensorManager.getSensorList(
                Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0) {
            sensor = sensors.get(0);
            running = sensorManager.registerListener(
                    sensorEventListener, sensor, 
                    SensorManager.SENSOR_DELAY_GAME);
            listener = accelerometerListener;
        }
*/
    if(Config.LOGD) Log.i(LOGTAG, "startListening registerListener sensorEventListener="+sensorEventListener+" sensorManager="+sensorManager+" GY="+sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)+" *******************************");

        sensorManager.registerListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_UI);
        listener = accelerometerListener;
    }
 
    /**
     * Configures threshold and interval
     * And registers a listener and start listening
     * @param accelerometerListener
     *             callback for accelerometer events
     * @param threshold
     *             minimum acceleration variation for considering shaking
     * @param interval
     *             minimum interval between to shake events
     */
    public static void startListening(
            Activity activity, 
            AccelerometerListener accelerometerListener, 
            float threshold, int interval) {
        configure(threshold, interval);
        startListening(activity,accelerometerListener);
    }
 
    /**
     * The listener that listen to events from the accelerometer listener
     */
    private static SensorEventListener sensorEventListener = 
        new SensorEventListener() {
 
        private long now = 0;
        private long lastUpdate = 0;
        private long timeDiff = 0;
 
        private float x = 0;
        private float y = 0;
        private float z = 0;
        private float lastX = 0;
        private float lastY = 0;
        private float lastZ = 0;
        private float force = 0;
 
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
 
        public void onSensorChanged(SensorEvent event) {

/*
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
              //updateOrientation(event.values[0], event.values[1], event.values[2]);
              listener.onAccelerationChanged(x, y, z);
            }
*/

            // use the event timestamp as reference
            // so the manager precision won't depends 
            // on the AccelerometerListener implementation
            // processing time
            now = event.timestamp;
            if(lastUpdate==0)
              lastUpdate = now;

            x = event.values[0];    // heading 
            // Azimuth, rotation around the Z axis (0<=azimuth<360). 0 = North, 90 = East, 180 = South, 270 = West 

            y = event.values[1];    // pitch:  
            // rotation around X axis (-180<=pitch<=180), with positive values when the z-axis moves toward the y-axis. 

            z = event.values[2];    // roll
            // rotation around Y axis (-90<=roll<=90), with positive values when the z-axis moves toward the x-axis. 

            timeDiff = now - lastUpdate;
            if (timeDiff > 0) {
                force = Math.abs(x + y + z - lastX - lastY - lastZ); 

//    if(Config.LOGD) Log.i(LOGTAG, "sensorEventListener force="+force+" threshold="+threshold+" tdiff="+timeDiff+" interval="+interval);

                if (force > threshold) {
                    if (timeDiff >= interval) {
                        // trigger change event
                        listener.onAccelerationChanged(x, y, z);
                        lastX = x;
                        lastY = y;
                        lastZ = z;
                        lastUpdate = now;
                    }

                }
            }
        }
 
    };
 
}

