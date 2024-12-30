package com.example.tbt_s300;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
//import android.support.annotation.RequiresApi;
//import android.support.v4.app.ActivityCompat;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
//import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.media.SoundPool;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends Activity implements Runnable, View.OnClickListener, OnMapReadyCallback {
    /** tag. */
    private static final String TAG = "BluetoothSample";

    /** Bluetooth Adapter.  */
    private BluetoothAdapter mAdapter;

    /** Bluetoothデバイス. */
    private BluetoothDevice mDevice;

    /** Bluetooth UUID.(値は何でもいいよ) */
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /** esp32のデバイス名. */
    private final String DEVICE_NAME = "ESP32-s300";

    /** Soket. */
    private BluetoothSocket mSocket;

    /** Thread. */
    private Thread mThread;

    /** Threadの状態を表す. */
    private boolean isRunning;

    /** 接続ボタン. */
    private Button connectButton;

    /** ステータス. */
    private TextView mStatusTextView;

    /** Bluetoothから受信した値. */
    private TextView speedTextView;
    private TextView rotationTextView;
    private TextView altitudeTextView;

    /** Action(ステータス表示). */
    private static final int VIEW_STATUS = 0;

    /** Action(取得文字列). */
    private static final int VIEW_INPUT1 = 1;
    private static final int VIEW_INPUT2 = 2;
    private static final int VIEW_INPUT3 = 3;

    /** Connect確認用フラグ */
    private boolean connectFlg = false;
    /** 時刻送信確認用フラグ */
    private boolean timeFlg = true;

    /** BluetoothのOutputStream. */
    OutputStream mmOutputStream = null;

    /** 受け取るデータ. */
    private double data;

    /** 機速. */
    private double speed;

    /** 高度. */
    private double altitude;

    /** ER角度 */
    private float eangle;
    private float rangle;

    /** マップ */
    private GoogleMap mMap;

    /** 琵琶湖 */
    private final LatLng biwako= new LatLng(35.329977,136.189374);

    /** 音システム */
    private SoundPool soundPool;
    private int soundOne, soundTwo;

    /** google サインイン */
    private GoogleSignInClient mGoogleSignInClient;
    private GoogleAccountCredential credential;
    // サインイン用intentを識別するためのID。0であることに意味はない
    private final int RC_SIGN_IN = 0;
    private int time = 0;//データ間隔

    @SuppressLint({"MissingPermission", "MissingInflatedId"})
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // MapFragmentの作成
        MapFragment mapFragment = MapFragment.newInstance();
        transaction.replace(R.id.mapView, mapFragment);
        transaction.commit();

        mapFragment.getMapAsync(this);

        mStatusTextView = findViewById(R.id.statusValue);
        speedTextView = findViewById(R.id.inputValue);
        rotationTextView = findViewById(R.id.inputValue2);
        altitudeTextView = findViewById(R.id.inputValue3);

        connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(this);

        // Bluetoothのデバイス名を取得
        // DVICE_NAMEでデバイス名を定義
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mStatusTextView.setText("bluetoothをONにするかペアリングしてください。");
        //権限要求その1　Bluetoothパーミッションが許可されているか確認します
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, 1);
                return;
            }
        }

        Set<BluetoothDevice> devices = mAdapter.getBondedDevices();
        for (BluetoothDevice device : devices) {//自動接続の場合はここらへん使われない
            if (device.getName().equals(DEVICE_NAME)) {
                mStatusTextView.setText("find: " + device.getName());
                mDevice = device;
            }
        }
        //googleアカウントサインイン関係
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope("https://www.googleapis.com/auth/spreadsheets"))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton("https://www.googleapis.com/auth/spreadsheets"));

        // もし前回起動時にサインインしていたら、サインイン不要
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            Log.i("Main", account.getDisplayName());
            credential.setSelectedAccount(account.getAccount());
        }
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);

        //音システム
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                // USAGE_MEDIA
                // USAGE_GAME
                .setUsage(AudioAttributes.USAGE_GAME)
                // CONTENT_TYPE_MUSIC
                // CONTENT_TYPE_SPEECH, etc.
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                // ストリーム数に応じて
                .setMaxStreams(2)
                .build();

        soundOne = soundPool.load(this, R.raw.siren, 1);

        if(altitude>10){
            soundPool.play(soundOne, 1.0f, 1.0f, 0, 0, 1);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // サインイン完了時の処理
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.i("Main", account.getDisplayName());
            credential.setSelectedAccount(account.getAccount());
        } catch (ApiException e) {
            Log.w("Main", "signInResult:failed code=" + e.getStatusCode());
        }
    }

    //現在時刻取得
    public static String getNowDate(){
        final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        final Date date = new Date(System.currentTimeMillis());
        return df.format(date);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(biwako,11.8f));
        enableMyLocation();
    }

    // 現在地表示するための権限要求※なくてもいいかも
    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        isRunning = false;
        try {
            mSocket.close();
        } catch (Exception e) {
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        InputStream mmInStream = null;

        Message valueMsg = new Message();
        valueMsg.what = VIEW_STATUS;
        valueMsg.obj = "接続...";
        mHandler.sendMessage(valueMsg);

        try {
            //権限要求その2
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions

                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                    return;
                }
            } else {
                // Bluetoothパーミッションが許可されているか確認します
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    // Bluetoothパーミッションを要求します
                    ActivityCompat.requestPermissions((Activity) this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, 1);
                    return;
                }
            }
            mSocket = mDevice.createRfcommSocketToServiceRecord(MY_UUID);
            mSocket.connect();
            mmInStream = mSocket.getInputStream();
            mmOutputStream = mSocket.getOutputStream();

            // InputStreamのバッファを格納
            byte[] buffer = new byte[1024];

            // 取得したバッファのサイズを格納
            int bytes;
            valueMsg = new Message();
            valueMsg.what = VIEW_STATUS;
            valueMsg.obj = "connected.";
            mHandler.sendMessage(valueMsg);

            connectFlg = true;
            //間隔を空けてデータベースに書き込み
            HttpTransport httpTransport = new com.google.api.client.http.javanet.NetHttpTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            Sheets sheetsService = new Sheets.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("TBT_s300")// アプリケーション名を指定するのだが、適当でいいっぽい
                    .build();

            Sheets.Spreadsheets.Values result = sheetsService.spreadsheets().values();
            Sheets.Spreadsheets.Values result0 = sheetsService.spreadsheets().values();
            while(isRunning) {

                // InputStreamの読み込み
                bytes = mmInStream.read(buffer);
                Log.i(TAG, "bytes=" + bytes);
                // String型に変換
                String readMsg = new String(buffer, 0, bytes);

                String result1 = null;
                String result2 = null;

                // null以外なら表示
                if (readMsg.trim() != null && !readMsg.trim().equals("")) {
                    Log.i(TAG, "value=" + readMsg.trim());

                    data = Double.parseDouble(readMsg);//int型だとなぜかエラーになるからdouble
                    speed = data % 1000 / 10;
                    altitude = ((int) data / 1000) / (double) 10;

                    result1 = speed + "m/s";
                    valueMsg = new Message();
                    valueMsg.what = VIEW_INPUT1;
                    valueMsg.obj = result1;
                    mHandler.sendMessage(valueMsg);

                    //result = rotation +"rpm";
                    //valueMsg.what = VIEW_INPUT2;
                    //valueMsg.obj = result;
                    //mHandler.sendMessage(valueMsg);

                    valueMsg = new Message();
                    result2 = altitude + "m";
                    valueMsg.what = VIEW_INPUT3;
                    valueMsg.obj = result2;
                    mHandler.sendMessage(valueMsg);
                }

                List<List<Object>> values = Arrays.asList(
                        Arrays.asList(getNowDate(), result1, result2)
                );

                ValueRange body = new ValueRange().setValues(values);
                try{
                    if(time>2 && result1 != null){
                        //上書き
                        // Spreadsheet idはURLのhttps://docs.google.com/spreadsheets/d/xxxx/...のxxx部分
                        result.update("1gqTylxrX-ZjlrUlW4Awckogl1MLdA6v495bjrD6DH5s", "Sheet1!E3:G3", body)
                                .setValueInputOption("RAW")
                                .execute();
                        //データ記録
                        result0.append("1gqTylxrX-ZjlrUlW4Awckogl1MLdA6v495bjrD6DH5s", "Sheet1!A:C", body)
                                .setValueInputOption("RAW")
                                .execute();
                        time=0;
                    }
                }catch (Exception e){
                    //valueMsg = new Message();
                    //valueMsg.what = VIEW_STATUS;
                    //valueMsg.obj = "Error1:" + e;
                    //mHandler.sendMessage(valueMsg);
                }
                Thread.sleep(100);
                time++;
            }

        }catch(Exception e){
            //エラーはいたときの処理（bluetoothが切断されるなど）
            valueMsg = new Message();
            valueMsg.what = VIEW_STATUS;
            valueMsg.obj = "Error1:" + e;
            mHandler.sendMessage(valueMsg);

            try{
                mSocket.close();
            }catch(Exception ee){}
            isRunning = false;
            connectFlg = false;

        }
    }

    @Override
    protected void onResume() {//自動接続
        super.onResume();
        if (!connectFlg) {
            mStatusTextView.setText("try connect");

            mThread = new Thread(this);
            // Threadを起動し、Bluetooth接続
            isRunning = true;
            mThread.start();
        }
    }

    @Override
    public void onClick(View v) {
        if(v.equals(connectButton)) {
            // 接続されていない場合のみ
            if (!connectFlg) {
                mStatusTextView.setText("try connect");

                mThread = new Thread(this);
                // Threadを起動し、Bluetooth接続
                isRunning = true;
                mThread.start();
            }
        }
    }
    /**
     * 描画処理はHandlerでおこなう
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int action = msg.what;
            String msgStr = (String)msg.obj;
            if(action == VIEW_INPUT1){
                speedTextView.setText(msgStr);
            }
            else if(action == VIEW_INPUT3){
                altitudeTextView.setText(msgStr);
            }
            else if(action == VIEW_STATUS){
                mStatusTextView.setText(msgStr);
            }
        }
    };

}