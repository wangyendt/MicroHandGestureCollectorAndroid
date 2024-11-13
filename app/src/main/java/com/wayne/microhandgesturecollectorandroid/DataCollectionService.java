package com.wayne.microhandgesturecollectorandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class DataCollectionService extends Service {

    private static final String CHANNEL_ID = "DataCollectionChannel";

    @Override
    public void onCreate() {
        super.onCreate();

        // 创建通知通道（Android 8.0及以上需要）
        createNotificationChannel();

        // 创建并启动前台通知
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Data Collection Running")
                .setContentText("Collecting sensor data in the background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(1, notification);

        // 在这里启动数据采集逻辑
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 启动服务的代码，确保服务不会被系统杀掉
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Data Collection Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}