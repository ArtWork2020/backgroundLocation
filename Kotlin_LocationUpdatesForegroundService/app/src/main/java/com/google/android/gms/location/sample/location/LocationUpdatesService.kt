/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.location.sample.location

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.location.BuildConfig
import com.android.location.R
import com.google.android.gms.location.*
import com.google.android.gms.location.sample.location.LocationUpdatesService

//import static com.google.android.gms.location.sample.location.BuildConfig.APPLICATION_ID;
class LocationUpdatesService : Service() {
    //
    private var mNotificationManager: NotificationManager? = null
    private val mBinder: IBinder = LocalBinder()

    // 横画面となっているかフラグ
    private var mIsScreenLandscaped = false

    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private var mLocationRequest: LocationRequest? = null

    /**
     * Provides access to the Fused Location Provider API.
     */
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Callback for changes in location.
     */
    private var mLocationCallback: LocationCallback? = null
    private var mServiceHandler: Handler? = null

    /**
     * 現在の位置情報
     */
    private var mLocation: Location? = null
    override fun onCreate() {
        SampleLogUtil.logDebug("in")

        // FusedLocationClientのインスタンス取得
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 位置情報の取得コールバッククラス。
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                // (取得に成功した) 位置情報を更新する
                notifyLocation(locationResult.lastLocation)
            }
        }

        // 取得したい位置情報のリクエストを生成する
        createLocationRequest()

        // (端末で最後に取得した) 位置情報を取得する
        lastLocation()

        // ハンドラー作成
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // デバイスがAndroid O以上の場合
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // NotificationChannelを生成する
            val mChannel = NotificationChannel(
                    CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager!!.createNotificationChannel(mChannel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        SampleLogUtil.logDebug("in")

        // Notificationの開始済みフラグを取得する
        val isStartedFromNotification = intent.getBooleanExtra(
                EXTRA_STARTED_FROM_NOTIFICATION, false)

        // Notificationが開始済みの場合
        if (isStartedFromNotification) {
            // 位置情報の取得要求を停止する
            removeLocationUpdates()
            // サービスを終了する
            stopSelf()
        }

        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    /*
     * 画面回転をした場合に呼ばれ、以下のライフサイクルとなる
     * MainActivityの再生成（onDestroy() → onCreate()）
     * LocationUpdatesServiceの再生成（onUnbind() → onDestroy() → onCreate() → onBind()）
     *
     * 横画面でバックグラウンドへ遷移した場合
     * LocationUpdatesService（onUnbind() → onDestroy()）
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        SampleLogUtil.logDebug("in" + "newConfig : " + newConfig.orientation) //　縦：1、横：2

        //　横画面となったフラグを設定する(true)
        mIsScreenLandscaped = true
    }

    // MainActivityがフォアグラウンドに来て、サービスにバインドした場合に呼ばれる
    override fun onBind(intent: Intent): IBinder? {
        SampleLogUtil.logDebug("in")

        // Notificationを消去する
        stopForeground(true)
        // 横画面となったフラグを設定する(false)
        mIsScreenLandscaped = false
        return mBinder
    }

    // Called when a client (MainActivity in case of this sample) returns to the foreground
    // and binds once again with this service. The service should cease to be a foreground
    // service when that happens.
    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        SampleLogUtil.logDebug("in")

        // Notificationを消去する
        stopForeground(true)
        // 横画面となったフラグを設定する(false)
        mIsScreenLandscaped = false
    }

    // Activityがバックグラウンドへ遷移した場合、呼ばれる
    override fun onUnbind(intent: Intent): Boolean {
        SampleLogUtil.logDebug("in")

        // 位置情報取得要求中の場合、バックグラウンドへ遷移した処理を以下とする
        //　・縦画面表示の場合は、位置情報取得は継続してNotification表示を開始する。　
        // ・横画面表示の場合は、位置情報取得は停止してNotification表示はしない。
        if (!mIsScreenLandscaped && Utils.requestingLocationUpdates(this)) {
            SampleLogUtil.logDebug("Starting Notification")

            // Notificationを設定する
            val notification = setNotification()
            // Notificationの表示開始する
            startForeground(NOTIFICATION_ID, notification)
        }

        // Ensures onRebind() is called when a client re-binds.
        return true
    }

    // Activityがバックグラウンドへ遷移した場合、呼ばれる
    override fun onDestroy() {
        SampleLogUtil.logDebug("in")
        mServiceHandler!!.removeCallbacksAndMessages(null)
    }

    /**
     * 位置情報の取得要求をする
     * [SecurityException].
     */
    fun requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates")

        // 位置情報要求中フラグを設定する(true)
        Utils.setRequestingLocationUpdates(this, true)

        // サービスの開始 (大体のケースで、onStartCommand()が呼ばれるかな？)
        val locationIntent = Intent(applicationContext, LocationUpdatesService::class.java)
        startService(locationIntent)
        try {
            // (FusedLocationClientに対して) 位置情報の取得要求をする
            mFusedLocationClient!!.requestLocationUpdates(
                    mLocationRequest,  // リクエスト内容
                    mLocationCallback,  // 位置情報取得コールバックの登録
                    Looper.myLooper())
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")

            // (不揮発領域へ)位置情報要求中フラグを保存する(false)
            Utils.setRequestingLocationUpdates(this, false)
        }
    }

    /**
     * 位置情報の取得要求を停止する
     */
    fun removeLocationUpdates() {
        SampleLogUtil.logDebug("in")
        try {
            // 位置情報の取得要求を停止する
            mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
            // フラグのリクエスト済みを削除する
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not remove updates. $unlikely")

            // (不揮発領域へ)位置情報要求中フラグを保存する(true)
            Utils.setRequestingLocationUpdates(this, true)
        }
    }

    /**
     * Notificationの設定をする
     */
    private fun setNotification(): Notification {

        // 取得した位置情報をテキスト設定する
        val locationText: CharSequence? = Utils.getLocationText(mLocation)
        val intent = Intent(this, LocationUpdatesService::class.java)
        // Notificationの開始済みフラグを設定する(true)
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        val servicePendingIntent = PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // The PendingIntent to launch activity.
        val activityPendingIntent = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), 0)

        // Notificationの設定
        val builder = NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),  // Notificationのボタン1
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),  // Notificationのボタン2
                        servicePendingIntent)
                .setContentText(locationText) // 取得した位置情報を設定
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true) // 取得した位置情報を設定
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(locationText)
                .setWhen(System.currentTimeMillis())

        // Android O以上の場合、NotificationへチャンネルIDを設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }
        return builder.build()
    }// 取得したLocationを保持する

    // (端末で最後に取得した)位置情報を取得する
//    private val lastLocation: Unit
    private fun lastLocation() {
//        private get() {
            try {
                mFusedLocationClient!!.lastLocation
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful && task.result != null) {

                                // 取得したLocationを保持する
                                mLocation = task.result
                            } else {
                                Log.w(TAG, "Failed to get location.")
                            }
                        }
            } catch (unlikely: SecurityException) {
                Log.e(TAG, "Lost location permission.$unlikely")
            }
//        }
    }

    // (取得に成功した) 位置情報を各所へ送信する
    private fun notifyLocation(location: Location) {
        Log.i(TAG, "位置情報: $location")

        // 取得したLocationを保持する
        mLocation = location

        // (取得に成功した) 位置情報をブロードキャスト配信する
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        // 本サービスがフォアグラウンドサービスの場合のみ
        if (isServiceRunningInForeground(this)) {

            // 位置情報をNotificationへ表示更新する
            mNotificationManager!!.notify(NOTIFICATION_ID, setNotification())
        }
    }

    /**
     * 位置情報リクエストを生成する
     */
    private fun createLocationRequest() {
        // リクエストインスタンスの生成
        mLocationRequest = LocationRequest()

        // 位置情報要求時の通知時間(ms)
        mLocationRequest!!.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        // 位置情報要求時の通知間隔(m)
        mLocationRequest!!.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        // 位置情報要求時の通知精度
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    /**
     * 本サービスがフォアグラウンドサービスかを判定する
     *
     * @param context The [Context].
     */
    fun isServiceRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val PACKAGE_NAME = BuildConfig.APPLICATION_ID
        private val TAG = LocationUpdatesService::class.java.simpleName

        // The name of the channel for notifications.
        private const val CHANNEL_ID = "channel_01"

        // The identifier for the notification displayed for the foreground service.
        private const val NOTIFICATION_ID = 12345678

        //
        const val ACTION_BROADCAST = PACKAGE_NAME + ".broadcast"

        //
        const val EXTRA_LOCATION = PACKAGE_NAME + ".location"

        // Notificationの開始済みフラグ
        private const val EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME + ".started_from_notification"

        // 位置情報要求時の通知時間(ms)
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

        // The fastest rate for active location updates. Updates will never be more frequent　than this value.
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
    }
}