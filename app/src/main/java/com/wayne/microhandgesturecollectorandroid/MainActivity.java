package com.wayne.microhandgesturecollectorandroid;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.wayne.microhandgesturecollectorandroid.utils.SensorUtils;
import com.wayne.lark_bot.*;
import com.wayne.lark_custom_bot.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements SensorEventListener {


    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, ppg;
    private TextView textViewData;
    private Button buttonToggle;
    private Spinner spinnerHand, spinnerGesture, spinnerForce;
    private EditText editTextNote;
    private FileWriter fileWriter;
    private boolean isCollecting = false;

    private String accData = "acc: N/A";
    private String gyroData = "gyro: N/A";
    private String ppgData = "ppg_hrm: N/A";

    private String user_open_id = "";

    private LarkBot bot;
    private FileWriter fileWriterAcc, fileWriterGyro;
    private File accFile, gyroFile;

    private FileWriter fileWriterPpg;
    private File ppgFile;

    private PowerManager.WakeLock wakeLock;

    private static final int PPG_TYPE = SensorUtils.TYPE_PPG_SENSOR;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        // 获取PowerManager服务
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // 创建WakeLock
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK |
                        PowerManager.ON_AFTER_RELEASE,
                "MicroHandGesture::WakeLockTag");

        // 获取WakeLock
        wakeLock.acquire();

        // 启动前台服务，确保数据采集在后台持续进行
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);  // Android 8.0 及以上
        } else {
            startService(serviceIntent);  // 低于 Android 8.0
        }

        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 1);
        }

        textViewData = findViewById(R.id.textViewData);
        buttonToggle = findViewById(R.id.buttonToggle);

        // Initialize Spinners
        spinnerHand = findViewById(R.id.spinnerHand);
        spinnerGesture = findViewById(R.id.spinnerGesture);
        spinnerForce = findViewById(R.id.spinnerForce);
        editTextNote = findViewById(R.id.editTextNote);


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        listAllSensors();
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // 添加PPG传感器初始化
        ppg = sensorManager.getDefaultSensor(PPG_TYPE);

        buttonToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCollecting) {
                    stopCollecting();
                } else {
                    startCollecting();
                }
            }
        });
    }

    private void listAllSensors() {
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : sensorList) {
            Log.d("SensorList", "Sensor: " + sensor.getName() + " - Type: " + sensor.getType() + " - StringType: " + sensor.getStringType());
        }
    }

    private void startCollecting() {
        buttonToggle.setText("Stop");
        isCollecting = true;

        spinnerHand.setEnabled(false);
        spinnerGesture.setEnabled(false);
        spinnerForce.setEnabled(false);
        editTextNote.setEnabled(false);

        // 获取当前选择的佩戴手、手势和力度
        String selectedHand = spinnerHand.getSelectedItem().toString();
        String selectedGesture = spinnerGesture.getSelectedItem().toString();
        String selectedForce = spinnerForce.getSelectedItem().toString();
        String note = editTextNote.getText().toString().trim();

        // 创建文件夹名称，包括时间和选择的选项
        String folderName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date())
                + "_" + selectedHand + "_" + selectedGesture + "_" + selectedForce
                + (note.isEmpty() ? "" : "_" + note);

        File folder = new File(getExternalFilesDir(null), folderName);
        if (!folder.exists()) folder.mkdirs();

        // 创建文件
        accFile = new File(folder, "acc.txt");
        gyroFile = new File(folder, "gyro.txt");

        // 创建PPG相关文件
        ppgFile = new File(folder, "ppg.txt");

        try {
            fileWriterAcc = new FileWriter(accFile);
            fileWriterGyro = new FileWriter(gyroFile);
            fileWriterPpg = new FileWriter(ppgFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 注册所有传感器监听器
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        if (ppg != null) sensorManager.registerListener(this, ppg, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void stopCollecting() {
        sensorManager.unregisterListener(this);
        isCollecting = false;
        buttonToggle.setText("Start");

        spinnerHand.setEnabled(true);
        spinnerGesture.setEnabled(true);
        spinnerForce.setEnabled(true);
        editTextNote.setEnabled(true);

        try {
            if (fileWriterAcc != null) fileWriterAcc.close();
            if (fileWriterGyro != null) fileWriterGyro.close();
            if (fileWriterPpg != null) fileWriterPpg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCollecting) return;

        long hardwareTimestamp = event.timestamp;
        String data = "";
        FileWriter writer = null;

        Log.d("SensorEvent", "Sensor Type: " + event.sensor.getType() +
                ", Name: " + event.sensor.getName() +
                ", String Type: " + event.sensor.getStringType());

        // 根据传感器类型处理数据
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accData = String.format(Locale.getDefault(), "%d,%f,%f,%f",
                    hardwareTimestamp, event.values[0], event.values[1], event.values[2]);
            try {
                if (fileWriterAcc != null) {
                    fileWriterAcc.write(accData + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroData = String.format(Locale.getDefault(), "%d,%f,%f,%f",
                    hardwareTimestamp, event.values[0], event.values[1], event.values[2]);
            try {
                if (fileWriterGyro != null) {
                    fileWriterGyro.write(gyroData + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (event.sensor.getType() == PPG_TYPE) {
            StringBuilder sb = new StringBuilder();
            sb.append(hardwareTimestamp);
            for (float value : event.values) {
                sb.append(",").append(value);
            }
            ppgData = sb.toString();
            try {
                if (fileWriterPpg != null) {
                    fileWriterPpg.write(ppgData + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 写入数据到文件
        if (writer != null && !data.isEmpty()) {
            try {
                writer.write(data + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 更新界面显示
        String displayData = accData + "\n\n" + gyroData + "\n\n" + ppgData;
        textViewData.setText(displayData);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCollecting();
        // 释放WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}