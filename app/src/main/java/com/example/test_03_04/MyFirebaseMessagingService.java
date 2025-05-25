package com.example.test_03_04;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMessaging";
    private static final String CHANNEL_ID = "default_channel";
    private static final String CHANNEL_NAME = "Default Channel";
    private static final String BASE_URL = "https://www.noshow2025.shop/api";
    private static final String FCM_TOKEN_ENDPOINT = "/fcm/token";
    private static final int NOTIFICATION_ID = 0;
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        if (remoteMessage.getNotification() != null) {
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            showNotification(notification.getTitle(), notification.getBody());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "새로운 FCM 토큰 발급: " + token);
        // 앱 로그인 상태일 때만 서버로 전송
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        if (!prefs.getString("jwt_token", "").isEmpty()) {
            sendTokenToServer(token);
        } else {
            Log.d(TAG, "로그인 전이라 FCM 토큰 서버 전송 보류");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendTokenToServer(String token) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL + FCM_TOKEN_ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jwtToken = getSharedPreferences("auth", MODE_PRIVATE)
                        .getString("jwt_token", "");
                if (!jwtToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                }

                JSONObject json = new JSONObject();
                json.put("fcmToken", token);
                
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "FCM 토큰 전송 실패: " + responseCode);
                } else {
                    Log.d(TAG, "FCM 토큰 전송 성공");
                }

            } catch (IOException e) {
                Log.e(TAG, "FCM 토큰 전송 중 네트워크 오류", e);
            } catch (Exception e) {
                Log.e(TAG, "FCM 토큰 전송 중 예기치 않은 오류", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showNotification(String title, String message) {
        if (title == null || message == null) {
            Log.w(TAG, "알림 제목 또는 메시지가 null입니다");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
