package com.builder.dontlift

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.ContentResolver
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import kotlinx.android.synthetic.main.activity_main.*
import org.altbeacon.beacon.*
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity(), BeaconConsumer {


    val neededPermissions =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    private var isFlashing = false
    private var isReading = false
    lateinit var sensorManger: SensorManager
    val nearbyMessage = Message(Build.MODEL.toByteArray())

    lateinit var beaconManager: BeaconManager
    lateinit var beaconTransmitter: BeaconTransmitter
    /*
    val hasCameraFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManger = getSystemService(SENSOR_SERVICE) as SensorManager
        ActivityCompat.requestPermissions(
            this,
            neededPermissions, 1234
        )
        beaconManager = BeaconManager.getInstanceForApplication(this)
        broadcast_btn.setOnClickListener {

            //sendPost()
            showKudosNotification()
            /*    if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    //Log.d("Shabaz", "Broadcasting $pattern")
                    //irManager.transmit(38000, pattern)

                    if (!isFlashing)
                        startFlashing("11011011010")
                    //else
                    //stopFlashing()

                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        neededPermissions, 1234
                    )
                }*/
        }
        read_btn.setOnClickListener {
            if (!isReading) {
                isReading = true
                startReading()
            } else {
                stopReading()
            }
        }
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT))
        beaconManager.bind(this)
        nearby_send.setOnClickListener { sendNearbyMessage("REMIND")/*toggleAdvertising(Build.MODEL)*/ }
        show_notification.setOnClickListener { showNotification() }

        end_ride.setOnClickListener { endRide() }
        registerForNearbyMessages()
    }

    var popUp: PopUp? = null
    var isEnded = false
    private fun endRide() {
        Log.d("Ride", "endRide")
        if (!Build.MODEL.toUpperCase().contains("REDMI")) {
            isEnded = false
            sendNearbyMessage("READ")
            popUp = PopUp.createPopup(this)
            popUp?.show()
            val runnable = Runnable {
                if(!isEnded) {
                    runOnUiThread { popUp?.setFailed() }
                }
            }
            Handler().postDelayed(runnable, 30000)
        }

    }


    private fun createChannel() {
        val soundUri = Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                    packageName + "/" + R.raw.vroom
        )
        Log.d("Noti", "createChannel")
        val name = "Helmet"
        val descriptionText = "Don't lift helmet"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel("SHABAZ", name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = descriptionText
                }
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            Log.d("Noti", "soundUri $soundUri")
            channel.setSound(soundUri, attributes)
            val channel2 = NotificationChannel(
                "SHABAZ_2",
                "Kudos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thanks"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel2)
        }

    }

    var i = 0
    var nId = 1212
    val kId = 3282
    private fun showKudosNotification() {

        createChannel()
        Log.d("Noti", "showKudosNotification")
        var builder = NotificationCompat.Builder(this, "SHABAZ_2")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Kudos received - 13")
            .setContentText(" Someone thanked you for keeping the helmets inside the bike")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Log.d("Noti", "Noti ID $kId")

        manager.notify(kId, builder.build())

    }

    private fun showNotification() {
        createChannel()
        Log.d("Noti", "showNotification")
        val soundUri = Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                    packageName + "/" + R.raw.vroom
        )

        Log.d("Noti", "soundUri $soundUri")
        var builder = NotificationCompat.Builder(this, "SHABAZ")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Please return the helmet")
            .setContentText("Hey, looks like the helmet is not kept inside bike, please ensure it for the next riders safety")
            .setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nId += 1
        Log.d("Noti", "Noti ID $nId")

        manager.notify(nId, builder.build())

    }

    override fun onBeaconServiceConnect() {

        Log.d("BLE", "onBeaconServiceConnect")

        val rangeNotifier = object : RangeNotifier {
            override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, p1: Region?) {
                beacons?.let {
                    if (beacons.isNotEmpty()) {
                        Log.d(
                            "BLE",
                            "didRangeBeaconsInRegion called with beacon count:  " + beacons.size
                        )
                        val firstBeacon = beacons.iterator().next()
                        val url =
                            UrlBeaconUrlCompressor.uncompress(firstBeacon.id1.toByteArray())
                        Log.d(
                            "BLE",
                            "The first beacon url " + url + " is about " + firstBeacon.distance + " meters away."
                        )
                        dist.text = firstBeacon.distance.roundToInt().toString()
                        if (firstBeacon.distance.compareTo(20.0) > 0) {

                            if(Build.MODEL.toUpperCase().contains("REDMI"))
                             sendNearbyMessage("REMIND")
                        }
                    }
                }
            }

        }
        try {
            beaconManager.startRangingBeaconsInRegion(
                Region(
                    "myRangingUniqueId",
                    null,
                    null,
                    null
                )
            )
            beaconManager.addRangeNotifier(rangeNotifier)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }


    }

    private fun registerForNearbyMessages() {
        Log.d("BLE", "registerForNearbyMessages")
        Nearby.getMessagesClient(this).subscribe(nearbyMessageListener)

    }

    var isAdvertising = false
    private fun toggleAdvertising(nearbyMessage: String) {

        if (!isAdvertising) {
            Log.d("BLE", "Advertising")
            isAdvertising = true
            //Nearby.getMessagesClient(this).publish(nearbyMessage)
            // Sets up to transmit as an AltBeacon-style beacon.  If you wish to transmit as a different
            // type of beacon, simply provide a different parser expression.  To find other parser expressions,
            // for other beacon types, do a Google search for "setBeaconLayout" including the quotes

            val urlBytes = UrlBeaconUrlCompressor.compress("https://www.shabaz.me")
            val encodedUrlIdentifier = Identifier.fromBytes(urlBytes, 0, urlBytes.size, false)
            val identifiers = ArrayList<Identifier>()
            identifiers.add(encodedUrlIdentifier)

            val beaconParser = BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT)

            beaconTransmitter = BeaconTransmitter(
                this,
                beaconParser
                /*BeaconParser().setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25")*/
            )
            // Transmit a beacon with Identifiers 2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6 1 2

            val beacon = Beacon.Builder()
                .setIdentifiers(identifiers)
                .setManufacturer(0x0118)
                .setTxPower(-59)
                .build()
            /*  val beacon = Beacon.Builder()
                  .setId1("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6")
                  .setId2("1")
                  .setId3("2")
                  .setManufacturer(0x0000) // Choose a number of 0x00ff or less as some devices cannot detect beacons with a manufacturer code > 0x00ff
                  .setTxPower(-59)
                  .setDataFields(arrayOf(0L).asList())
                  .build()*/

            beaconTransmitter.startAdvertising(beacon, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)
                    Log.d("BLE", "onStartSuccess")
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    Log.d("BLE", "onStartFailure $errorCode")
                }


            })
        } else {
            Log.d("BLE", "Stopping Advertising")
            beaconTransmitter.stopAdvertising()
            isAdvertising = false

        }

    }

    private val nearbyMessageListener = object : MessageListener() {
        override fun onFound(message: Message) {
            Log.d("NearBy", "Found message: " + String(message.content))
            Toast.makeText(
                this@MainActivity,
                "Received ${String(message.content)}",
                Toast.LENGTH_LONG
            ).show()
            when (String(message.content)) {
                "READ" -> {
                    if (!isReading)
                        startReading()
                    else {
                        stopReading()
                        startReading()
                    }

                }
                "REMIND" -> {
                    showNotification()
                }
                "END" -> {
                    isEnded = true
                    popUp?.setDone()
                    sendPost()

                }
            }
        }

        /**
         * Called when the Bluetooth Low Energy (BLE) signal associated with a message changes.
         *
         * This is currently only called for BLE beacon messages.
         *
         * For example, this is called when we see the first BLE advertisement
         * frame associated with a message; or when we see subsequent frames with
         * significantly different received signal strength indicator (RSSI)
         * readings.
         *
         * For more information, see the MessageListener Javadocs.
         */
        override fun onBleSignalChanged(message: Message, bleSignal: BleSignal) {
            Log.i(
                "BLE",
                "Message: $message has new BLE signal information: $bleSignal"
            )
        }

        /**
         * Called when Nearby's estimate of the distance to a message changes.
         *
         * This is currently only called for BLE beacon messages.
         *
         * For more information, see the MessageListener Javadocs.
         */
        override fun onDistanceChanged(message: Message, distance: Distance) {
            Log.i(
                "BLE",
                "Distance changed, message: $message, new distance: $distance"
            )
        }

        override fun onLost(message: Message) {
            Log.d("BLE", "onLost message: " + String(message.content))
            Toast.makeText(this@MainActivity, "Lost ${String(message.content)}", Toast.LENGTH_LONG)
                .show()

        }
    }

    fun sendPost() {
        val thread = Thread(Runnable {
            try {
                Log.d("POST", "http://139.59.61.212:8008/mongo")
                val url = URL("http://139.59.61.212:8008/mongo")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.doInput = true

                val jsonParam = JSONObject()
                jsonParam.put("centreId", 2)
                jsonParam.put("helmetReturned", true)
                jsonParam.put("updatedAt", 9)

                Log.i("POST", jsonParam.toString())
                val os = DataOutputStream(conn.outputStream)
                os.writeBytes(jsonParam.toString())

                os.flush()
                os.close()

                Log.i("POST","STATUS "+ conn.responseCode.toString())
                Log.i("POST","MSG"+conn.responseMessage)

                conn.disconnect()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        })

        thread.start()
    }

    private fun stopReading() {
        Log.d("NearBy", "Stop reading")
        readRawBits = ""
        sensorManger.unregisterListener(lightListener)
        isReading = false
    }

    private fun sendNearbyMessage(command: String) {
        Log.d("Nearby", "sendNearbyMessage $command")
        val message = Message(command.toByteArray())
        val publishSubscriptionStrategy = Strategy.Builder().setTtlSeconds(6).build()
        val option: PublishOptions =
            PublishOptions.Builder().setStrategy(publishSubscriptionStrategy)
                .setCallback(object : PublishCallback() {
                    override fun onExpired() {
                        super.onExpired()
                        Log.i("Nearby", "Message Publish expired $command")
                    }
                }).build()
        Nearby.getMessagesClient(this).publish(message, option)
    }

    private fun stopFlashing() {
        Log.d("Shabaz", "stopFlashing")
        isFlashing = false
    }

    private fun startFlashing(s: String) {
        Log.d("Shabaz", "startFlashing pattern $s")
        isFlashing = true
        val flasher = object : Thread() {
            override fun run() {
                val sequence = s.split("")
                for (state in sequence) {
                    Log.d("Shabaz", "State $state")
                    if (state == "1") flashLightOn()
                    else if (state == "0") flashLightOff()
                    sleep(1000)
                }
                isFlashing = false
            }
        }
        flasher.start()
    }

    private fun startReading() {
        Log.d("Shabaz", "startReading")
        sensorManger.registerListener(
            lightListener, sensorManger.getDefaultSensor(
                Sensor.TYPE_LIGHT
            ), SensorManager.SENSOR_DELAY_FASTEST
        )


    }

    override fun onPause() {
        super.onPause()

        if (beaconManager.isBound(this)) beaconManager.backgroundMode = true
    }

    override fun onResume() {
        super.onResume()
        if (beaconManager.isBound(this)) beaconManager.backgroundMode = false
    }

    override fun onDestroy() {
        try {
            beaconTransmitter.let { it.stopAdvertising() }
            beaconManager.let { it.unbind(this) }
            Nearby.getMessagesClient(this).unpublish(nearbyMessage)
            Nearby.getMessagesClient(this).unsubscribe(nearbyMessageListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()


    }

    var readRawBits = ""
    var idBits = ""
    var lastReadTime = Date().time
    var lastIntensity = -1.0F
    var highCount = 0
    private val lightListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.d("Shabaz", "onAccuracyChanged")
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val currentIntensity = event.values[0]
                Log.d("Shabaz", "onSensorChanged $currentIntensity")
                Log.d("Shabaz", "readRawBits $readRawBits")
                lastIntensity = currentIntensity

                val currentTime = Date().time
                Log.d("Read", "Time Elapsed = ${(currentTime - lastReadTime)}")
                if ((currentTime - lastReadTime) > 900) {
                    val multiplier = (currentTime - lastReadTime) / 900
                    Log.d("Shabaz", "Multiplier = ${(currentTime - lastReadTime) / 900}")

                    lastReadTime = currentTime
                    if (currentIntensity.compareTo(230) >= 0) {//atleast 700 millisecs
                        highCount += 1
                        if (multiplier < 5)
                            for (j in multiplier downTo 0 step 1)
                                readRawBits += "1"
                    } else {
                        if (multiplier < 5)
                            for (j in multiplier downTo 0 step 1)
                                readRawBits += "0"
                    }



                    if (readRawBits.contains("111")) {
                        if (currentIntensity.compareTo(100) >= 0) {//atleast 700 millisecs
                            idBits += "1"
                        } else {
                            idBits += "0"
                        }
                    }
                    if (idBits.length > 8) {
                        Log.d("Shabaz", "Id bits $idBits")
                        Log.d("Shabaz", "Found Id ${getId(idBits)}")
                        id_text.text = idBits
                        sendNearbyMessage("END")
                        stopReading()
                        idBits = ""
                        readRawBits = ""
                    }
                }

            }
        }

    }

    private fun getId(idBits: String): Int {
        Log.d("Shabaz", "getId $idBits")
        var id = 0
        var temp = 0
        var base = 2.0
        for (i in 7 downTo 0 step 1) {
            if (idBits.get(i) == '1') {
                temp = base.pow(i).toInt()
            }
            id += temp
        }
        return id
    }

    private fun flashLightOn() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, true)
        } catch (e: CameraAccessException) {
        }

    }

    private fun flashLightOff() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, false)
        } catch (e: CameraAccessException) {
        }

    }
}
