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

import android.Manifest
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.location.BuildConfig
import com.android.location.R
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.sample.location.LocationUpdatesService.LocalBinder
import com.google.android.gms.location.sample.location.MainActivity
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    // The BroadcastReceiver used to listen from broadcasts from the service.
    private var myReceiver: LocationReceiver? = null

    // A reference to the service used to get location updates.
    private var mService: LocationUpdatesService? = null

    // Tracks the bound state of the service.
    private var mBound = false

    // 位置情報の取得要求、取得要求の停止ボタン
    private var mRequestLocationUpdatesButton: Button? = null
    private var mRemoveLocationUpdatesButton: Button? = null

    // Monitors the state of the connection to the service.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // debugビルドの場合のみ、ログ出力を有効にする
        if (BuildConfig.DEBUG) {
            SampleLogUtil.setShowLog(true)
        }
        SampleLogUtil.logDebug("in")
        myReceiver = LocationReceiver()
        setContentView(R.layout.activity_main)

        // Check that the user hasn't revoked permissions by going to Settings.
        if (Utils.requestingLocationUpdates(this)) {

            // デバイスの位置情報設定ON／OFFチェック
            requestLocationSetting()

        }
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this)
        mRequestLocationUpdatesButton = findViewById<View>(R.id.request_location_updates_button) as Button
        mRemoveLocationUpdatesButton = findViewById<View>(R.id.remove_location_updates_button) as Button

        // 位置情報の取得要求ボタン
        mRequestLocationUpdatesButton!!.setOnClickListener {

            // デバイスの位置情報設定ON／OFFチェック
            requestLocationSetting()

            if (!checkAllPermissions()) {
                requestPermissions()
            } else {
                // 位置情報の取得要求を開始する
                mService!!.requestLocationUpdates()
            }
        }

        // 位置情報の取得要求の停止ボタン
        mRemoveLocationUpdatesButton!!.setOnClickListener { // 位置情報の取得要求を停止する
            mService!!.removeLocationUpdates()
        }

        // Restore the state of the buttons when the activity (re)launches.
        setButtonsState(Utils.requestingLocationUpdates(this))

        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(Intent(this, LocationUpdatesService::class.java), mServiceConnection,
                BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()

        // 位置情報をトースト表示するレシーバーへアクション登録をする
        val filter = IntentFilter(LocationUpdatesService.Companion.ACTION_BROADCAST)
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver!!, filter)
    }

    override fun onPause() {
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver!!)
        super.onPause()
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver!!)

        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(mServiceConnection)
            mBound = false
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        SampleLogUtil.logDebug("in")
    }

    private fun checkAllPermissions(): Boolean {
        // 位置情報系(フォアグラウンド & バックグラウンド)のパーミッション許可取得済みかチェック
        val isLocationPermissionGranted: Boolean =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
        return isLocationPermissionGranted
    }

    /**
     * Returns the current state of the permissions needed.
     */
    private fun checkForegroundPermissions(): Boolean {
        // 位置情報系(フォアグラウンド)のパーミッション許可取得済みかチェック
        val isLocationPermissionGranted: Boolean =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
        return isLocationPermissionGranted
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkBackgroundPermissions(): Boolean {
        // 位置情報系(バックグラウンド)のパーミッション許可取得済みかチェック
        val isLocationPermissionGranted: Boolean =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
        return isLocationPermissionGranted
    }

    private fun requestPermissions() {
        // ユーザーが過去に「今後は確認しない（継続的に不許可）」を選んでいた場合、falseを返却する
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION)

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok) { // Request permission
                        ActivityCompat.requestPermissions(this@MainActivity,
                                arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                ),
                                REQUEST_PERMISSIONS_REQUEST_CODE)
                    }
                    .show()

            //デバイスがAndroid P(API 29)以上の場合
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // フォアグラウンドのパーミッション許可取得済みかをチェック
                val foregroundLocationPermissionApproved
                        = checkForegroundPermissions()

                // フォアグラウンドのパーミッション許可取得済みの場合
                if (foregroundLocationPermissionApproved) {
                    // バックグラウンドのパーミッション許可取得済みかをチェック
                    val backgroundLocationPermissionApproved
                            = checkBackgroundPermissions()

                    // バックグランドのバーミッション許可取得済みの場合
                    if (backgroundLocationPermissionApproved) {
                        // パーミッション許可のリクエストは不要
                    } else {
                        // バックグラウンドのパーミッション許可がない場合
                        // バックグラウンドの許可のみを求める
                        ActivityCompat.requestPermissions(this,
                                arrayOf(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ),
                                REQUEST_PERMISSIONS_REQUEST_CODE
                        )
                    }
                } else {
                    // 位置情報の権限が1つも無いため、全ての許可を求める
                    ActivityCompat.requestPermissions(this,
                            arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ),
                            REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                }


            } else {
            //デバイスがAndroid O(API 28)以下の場合

                // フォアグラウンドのパーミッション許可取得済みかをチェック
                val foregroundLocationPermissionApproved
                        = checkForegroundPermissions()

                // フォアグラウンドのパーミッション許可　未取得の場合
                if (!foregroundLocationPermissionApproved) {
                    // フォアグラウンドパーミッション　取得要求
                    ActivityCompat.requestPermissions(
                            this@MainActivity, arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION),
                            REQUEST_PERMISSIONS_REQUEST_CODE)
                } else {
                    // フォアグラウンドのパーミッション許可　取得済みの場合、何もしない
                }
            }


            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok) {
                        // Request permission
                        ActivityCompat.requestPermissions(this@MainActivity,
                                arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION),
                                REQUEST_PERMISSIONS_REQUEST_CODE)
                    }
                    .show()
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }

    // デバイスの位置情報設定ON／OFFチェック
    private fun requestLocationSetting() {

        val request: LocationSettingsRequest = LocationSettingsRequest.Builder()
                .setAlwaysShow(true)
                // 可能な限り最も高精度の位置情報をリクエストする
                .addLocationRequest(LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY))
                .build()
        val result: Task<LocationSettingsResponse> = LocationServices.getSettingsClient(this)
                .checkLocationSettings(request)
        result.addOnCompleteListener(object : OnCompleteListener<LocationSettingsResponse?> {
            override fun onComplete(task: Task<LocationSettingsResponse?>) {
                try {
                    task.getResult(ApiException::class.java)

                    // 既に設定済みの場合
                    // パーミッション許可が１つでも取れていない場合
                    if (!checkAllPermissions()) {
                        // パーミッション許可リクエスト
                        requestPermissions()
                    }

                } catch (exception: ApiException) {
                    when (exception.getStatusCode()) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {

                            // Cast to a resolvable exception.
                            val resolvable: ResolvableApiException = exception as ResolvableApiException
                            // ユーザに位置情報設定を変更してもらうためのダイアログを表示する
                            resolvable.startResolutionForResult(this@MainActivity, REQUEST_CODE_LOCATION_SETTING)
                            return
                        } catch (e: IntentSender.SendIntentException) {
                            // Ignore the error.
                        } catch (e: ClassCastException) {
                            // Ignore, should be an impossible error.
                        }
                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        }
                    }
//                    finishForResult(Locset.ResultCode.LOCATION_SETTING_FAILURE)
                }
            }
        })
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.size <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // パーミッション許可が得られたため、位置情報の取得要求をする
                mService!!.requestLocationUpdates()
            } else {
                // Permission denied.
                setButtonsState(false)
                Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings) { // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts("package",
                                    BuildConfig.APPLICATION_ID, null)
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
            }
        } else if (requestCode == REQUEST_CODE_LOCATION_SETTING) {
            // パーミッション許可が１つでも取れていない場合
            if (!checkAllPermissions()) {
                // パーミッション許可リクエスト
                requestPermissions()
            }
        }
    }

    /**
     * サービスからの位置情報を受信するレシーバー
     */
    private inner class LocationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(LocationUpdatesService.Companion.EXTRA_LOCATION)
            if (location != null) {
                // 位置情報をトースト表示する
                Toast.makeText(this@MainActivity, Utils.getLocationText(location),
                        Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String) {
        SampleLogUtil.logDebug("in")

        // 位置情報要求中の場合
        if (s == Utils.KEY_REQUESTING_LOCATION_UPDATES) {
            // (不揮発領域へ) ボタン状態を更新する
            setButtonsState(sharedPreferences.getBoolean(
                    Utils.KEY_REQUESTING_LOCATION_UPDATES, false))
        }
    }

    // 位置情報の取得要求・要求停止ボタンの有効／無効を制御する
    private fun setButtonsState(requestingLocationUpdates: Boolean) {

        // 位置情報の取得要求済みの場合
        if (requestingLocationUpdates) {
            mRequestLocationUpdatesButton!!.isEnabled = false
            mRemoveLocationUpdatesButton!!.isEnabled = true
            SampleLogUtil.logDebug("in false, true")
        } else {
            // 位置情報の取得要求が停止中の場合
            mRequestLocationUpdatesButton!!.isEnabled = true
            mRemoveLocationUpdatesButton!!.isEnabled = false
            SampleLogUtil.logDebug("in true, false")
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        // Used in checking for runtime permissions.
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34

        // デバイスの位置情報ON・OFFリクエスト
        private const val REQUEST_CODE_LOCATION_SETTING = 100
    }
}