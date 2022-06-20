package com.example.heartrate_streamer

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt


class HeartRateService : Service(), SensorEventListener2 {
    private final var STOP_ACTION = "STOP_ACTION"
    private lateinit var mSensorManager : SensorManager
    private lateinit var mHeartRateSensor: Sensor
    var sHost = ""
    private lateinit var wakeLock : PowerManager.WakeLock
    private lateinit var notificationManager: NotificationManager
    private val NOTIFICATION_ID = 12345678

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if(intent.action == STOP_ACTION){
               stopSelf();
                notificationManager.cancel(NOTIFICATION_ID);
                android.os.Process.killProcess(android.os.Process.myPid());
            }
            if (intent.getAction() == "updateHost") {
                sHost = intent.getExtras()?.get("host").toString();
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        var intentFilter = IntentFilter();
        intentFilter.addAction(STOP_ACTION);
        intentFilter.addAction("updateHost");
        registerReceiver(broadcastReceiver, intentFilter);

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run{
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartWear::BackgroundStreaming").apply{
                acquire();
            }
        }
        var getHostIntent = Intent();
        getHostIntent.action = "getHost";
        sendBroadcast(getHostIntent);

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver);
        mSensorManager.unregisterListener(this);

        wakeLock.release();
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        createNotificationChannel();
        var notificationIntent = Intent(this, MainActivity::class.java);
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )

        val stopIntent = Intent();
        stopIntent.action = STOP_ACTION;
        var pendingIntentStopAction = PendingIntent.getBroadcast(this, 12345, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);



        val notification = NotificationCompat.Builder(this, "hrservice")
            .setContentTitle("HeartWear")
            .setContentText("Streaming heart rate in the background...")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingIntentStopAction)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build();

        startForeground(1, notification)

        mHeartRateSensor?.also { heartRate ->
            mSensorManager.registerListener(this, heartRate, SensorManager.SENSOR_DELAY_NORMAL)
        }

        return START_STICKY;
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "hrservice",
                "HeartWear Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onFlushCompleted(p0: Sensor?) {
    }

    private var oldRoundedHeartRate : Int = 0;
    private var oldBatteryPercent : Int = 0;

    // Called when the sensor has a new value
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(p0: SensorEvent?) {
        var heartRate: Float? = p0?.values?.get(0) ?: return;
        var roundedHeartRate = (heartRate!!).roundToInt();
        if(roundedHeartRate == oldRoundedHeartRate) return;     // If the heart rate only changes a bit, don't bother to update the database.

        var updateHRIntent = Intent();
        updateHRIntent.action = "updateHR";
        updateHRIntent.putExtra("bpm", roundedHeartRate);
        this.sendBroadcast(updateHRIntent);

        val sendData : String  = roundedHeartRate.toString()
        if (!this.sHost.isEmpty()) {
            try {
                DatagramSocket().send(
                    DatagramPacket(
                        sendData.toByteArray(),
                        sendData.length,
                        InetAddress.getByName(sHost),
                        2115
                    )
                )
            } catch (e: Exception) {
                //Toast.makeText(baseContext, "Network Error", Toast.LENGTH_LONG).show();
                //Toast.makeText(baseContext, e.message.toString(), Toast.LENGTH_LONG).show();
            }
        }
        notificationManager.notify(NOTIFICATION_ID,generateNotification("jo"))
    }

    private fun getBatteryPercentage(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 21) {
            val bm: BatteryManager =
                context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)
            val level = batteryStatus?.getIntExtra(
                BatteryManager.EXTRA_LEVEL,
                -1
            ) ?: -1
            val scale = batteryStatus?.getIntExtra(
                BatteryManager.EXTRA_SCALE,
                -1
            ) ?: -1
            val batteryPct = level / scale.toDouble()
            (batteryPct * 100).toInt()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun generateNotification(mainText: String): Notification {

        // 0. Get data (note, the main notification text comes from the parameter above).
        val titleText = "Heart2Network"

        // 1. Create Notification Channel.
        val notificationChannel = NotificationChannel(
            "walking_workout_channel_01", titleText, NotificationManager.IMPORTANCE_DEFAULT)

        // Adds NotificationChannel to system. Attempting to create an
        // existing notification channel with its original values performs
        // no operation, so it's safe to perform the below sequence.
        notificationManager.createNotificationChannel(notificationChannel)

        // 2. Build the BIG_TEXT_STYLE.
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(mainText)
            .setBigContentTitle(titleText)

        // 3. Set up main Intent/Pending Intents for notification.
        val launchActivityIntent = Intent(this, MainActivity::class.java)

        val cancelIntent = Intent(this, HeartRateService::class.java)
        cancelIntent.action = "STOP_ACTION"

        val servicePendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, launchActivityIntent, 0
        )

        // 4. Build and issue the notification.
        val notificationCompatBuilder =
            NotificationCompat.Builder(applicationContext, "walking_workout_channel_01")

        // TODO: Review Notification builder code.
        val notificationBuilder = notificationCompatBuilder
            .setStyle(bigTextStyle)
            .setContentTitle(titleText)
            .setContentText(mainText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // Makes Notification an Ongoing Notification (a Notification with a background task).
            .setOngoing(true)
            // For an Ongoing Activity, used to decide priority on the watch face.
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.close_button, "H2N",
                activityPendingIntent
            )
            .addAction(
                R.drawable.ic_full_cancel,
                "joop",
                servicePendingIntent
            )

        // TODO: Create an Ongoing Activity.
        val ongoingActivityStatus = Status.Builder()
            // Sets the text used across various surfaces.
            .addTemplate(mainText)
            .build()

        val ongoingActivity =
            OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
                // Sets icon that will appear on the watch face in active mode. If it isn't set,
                // the watch face will use the static icon in active mode.
                // Supported animated icon types: AVD and AnimationDrawable.
                .setAnimatedIcon(R.drawable.outline_monitor_heart_red_900_24dp)
                // Sets the icon that will appear on the watch face in ambient mode.
                // Falls back to Notification's smallIcon if not set. If neither is set,
                // an Exception is thrown.
                .setStaticIcon(R.drawable.outline_monitor_heart_red_900_24dp)
                // Sets the tap/touch event, so users can re-enter your app from the
                // other surfaces.
                // Falls back to Notification's contentIntent if not set. If neither is set,
                // an Exception is thrown.
                .setTouchIntent(activityPendingIntent)
                // In our case, sets the text used for the Ongoing Activity (more options are
                // available for timers and stop watches).
                .setStatus(ongoingActivityStatus)
                .build()

        // Applies any Ongoing Activity updates to the notification builder.
        // This method should always be called right before you build your notification,
        // since an Ongoing Activity doesn't hold references to the context.
        ongoingActivity.apply(applicationContext)

        return notificationBuilder.build()
    }
}
