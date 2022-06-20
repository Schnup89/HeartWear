package com.example.heartrate_streamer

/*import com.google.android.gms.auth.api.signin.GoogleSignIn
//import com.google.android.gms.auth.api.signin.GoogleSignInClient
//import com.google.android.gms.auth.api.signin.GoogleSignInOptions
//import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase */

//import android.R

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.widget.BoxInsetLayout


class MainActivity : WearableActivity() {
    val PREFS_NAME: String? = "HRSettings"

    private var mDetector: GestureDetector? = null

    private var broadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action == "updateHR") {
                var bpm: Any? = intent?.extras?.get("bpm") ?: return;
                var uiTextBPM: TextView = findViewById(R.id.textBPM)
                uiTextBPM.text = "$bpm bpm";
            }
            if(intent?.action == "getHost") {
                var uiedit : EditText = findViewById(R.id.inp_settings_host)
                tellHost(uiedit.text.toString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission(Manifest.permission.BODY_SENSORS, 100);

        // Enables Always-on
        setAmbientEnabled()

        // Register Intents
        val filter = IntentFilter()
        filter.addAction("updateHR")
        filter.addAction("getHost")
        registerReceiver(broadcastReceiver, filter)

        // UI Interactions
        var mScreen : BoxInsetLayout = findViewById(R.id.mainScreen)
        var uititle : TextView = findViewById(R.id.titleText)
        var uiTextBPM : TextView = findViewById(R.id.textBPM)
        var uiedit : EditText = findViewById(R.id.inp_settings_host)
        var uiexit : ImageButton = findViewById(R.id.btn_exit)


        //Host Text changed Editbox
        uiedit.setText(getApplicationContext().getSharedPreferences(PREFS_NAME, 0).getString("settings_host", ""));
        uiedit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                var uiedit : EditText = findViewById(R.id.inp_settings_host)
                val editor = applicationContext.getSharedPreferences(PREFS_NAME, 0).edit()
                editor.putString("settings_host", uiedit.text.toString())
                editor.apply()
                tellHost(uiedit.text.toString())
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        // Long press to show/hide UI for energy saving
        mScreen.setOnLongClickListener{
            if(uiTextBPM.visibility == View.VISIBLE)
                Toast.makeText(this, "Long press to show UI again", Toast.LENGTH_LONG).show();
            uiTextBPM.visibility = if(uiTextBPM.visibility == View.VISIBLE) View.GONE else View.VISIBLE;
            uiedit.visibility = if(uiedit.visibility == View.VISIBLE) View.GONE else View.VISIBLE;
            uititle.visibility = if(uititle.visibility == View.VISIBLE) View.GONE else View.VISIBLE;
            uiexit.visibility = if(uiexit.visibility == View.VISIBLE) View.GONE else View.VISIBLE;
            true;
        }
        uiexit.setOnClickListener{
            val stopintent = Intent()
            stopintent.action = "STOP_ACTION"
            sendBroadcast(stopintent)
        }
    }

    fun tellHost(sSndHost: String) {
        val updateHost = Intent()
        updateHost.action = "updateHost"
        updateHost.putExtra("host", sSndHost)
        sendBroadcast(updateHost)
    }


    fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this@MainActivity, permission)
            == PackageManager.PERMISSION_DENIED
        ) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(permission), requestCode)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart();

        Intent(applicationContext, HeartRateService::class.java).also { intent ->
            startForegroundService(intent);
        }
    }

    override fun onResume() {
        super.onResume();

    }

    override fun onPause() {
        super.onPause()

        Intent(this, HeartRateService::class.java).also { intent ->
            startService(intent);
        }
    }




}


