package com.zfdang.touchhelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class ForeGroundService extends Service {
  private static final String TAG = ForeGroundService.class.getSimpleName();
  NotificationManager notificationManager;
  String notificationId = "touch";
  String notificationName = "常驻服务";

  private void startForegroundService() {
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    //创建 NotificationChannel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(notificationId, notificationName, NotificationManager.IMPORTANCE_HIGH);
      notificationManager.createNotificationChannel(channel);
    }
    startForeground(1, getNotification());

  }

  private Notification getNotification() {
    Notification.Builder builder = new Notification.Builder(this)
        .setSmallIcon(R.drawable.ic_touch_helper_icon)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.app_name) + "正在运行...");

    //设置Notification的ChannelID,否则不能正常显示        
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder.setChannelId(notificationId);
    }
    Notification notification = builder.build();
    return notification;

  }

  @Override
  public void onCreate() {
    super.onCreate();
    startForegroundService();
    Log.d(TAG, "onCreate()");
  }


  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "onStartCommand()");
    return super.onStartCommand(intent, flags, startId);
  }


  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");
    stopForeground(true);// 停止前台服务--参数：表示是否移除之前的通知
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "onBind()");
    // TODO: Return the communication channel to the service.
    throw new UnsupportedOperationException("Not yet implemented");
  }
}