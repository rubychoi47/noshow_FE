package com.example.test_03_04;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QrCodeShow extends AppCompatActivity {
    private static final String TAG = "QrCodeShow";
    private static final int QR_CODE_SIZE = 512;
    private static final int QR_CODE_DELAY = 10000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final String BASE_URL = "https://www.noshow2025.shop/api";
    private static final String BREAK_ENDPOINT = "/qr/break";
    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ImageView qrImageView;
    private ImageButton closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qr_show);

        initializeViews();
        setupCloseButton();
        processUserInfo();
    }

    private void initializeViews() {
        qrImageView = findViewById(R.id.qrCodeImageView);
        closeButton = findViewById(R.id.closeButton);
    }

    private void setupCloseButton() {
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "닫기 버튼 클릭");
                finish();
            });
        } else {
            Log.e(TAG, "closeButton을 찾을 수 없습니다");
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(QrCodeShow.this, message, Toast.LENGTH_SHORT).show());
    }

    private void processUserInfo() {
        String studentId = getIntent().getStringExtra("studentId");
        String name = getIntent().getStringExtra("name");
        int entry = getIntent().getIntExtra("entry", -2);

        if (validateUserInfo(studentId, name)) {
            String qrData = createQrData(studentId, name);
            generateAndDisplayQrCode(qrData);

            if (entry == 1) {
                Log.d(TAG, "Entry 값 1 확인, 예약 여부 확인 시작");
                checkIfReserved();
            } else {
                Log.d(TAG, "Entry 값 1이 아님: " + entry + ", 퇴실/외출 처리 대기 로직 실행");
                scheduleEntryCheck(entry);
            }
        } else {
            showToast("사용자 정보가 올바르지 않습니다.");
            finish();
        }
    }

    private boolean validateUserInfo(String studentId, String name) {
        return studentId != null && !studentId.isEmpty() && name != null && !name.isEmpty();
    }

    private String createQrData(String studentId, String name) {
        return String.format("StudentId: %s, Name: %s", studentId, name);
    }

    private void generateAndDisplayQrCode(String qrData) {
        executorService.execute(() -> {
            Bitmap qrBitmap = generateQRCode(qrData);
            if (qrBitmap != null) {
                mainHandler.post(() -> qrImageView.setImageBitmap(qrBitmap));
            } else {
                mainHandler.post(() -> {
                    showToast("QR 코드 생성에 실패했습니다.");
                    finish();
                });
            }
        });
    }

    private void scheduleEntryCheck(int entry) {
        mainHandler.postDelayed(() -> {
            int currentEntry = getIntent().getIntExtra("entry", -2);
            if (currentEntry == 1) {
                showExitOrAwayDialog();
            } else {
                showToast("QR 코드 인식이 필요합니다.");
                finish();
            }
        }, QR_CODE_DELAY);
    }

    private Bitmap generateQRCode(String content) {
        try {
            Hashtable<EncodeHintType, String> hints = new Hashtable<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    QR_CODE_SIZE,
                    QR_CODE_SIZE,
                    hints
            );

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? BLACK : WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "QR 코드 생성 오류", e);
            return null;
        }
    }

    private void showExitOrAwayDialog() {
        new AlertDialog.Builder(this)
                .setTitle("퇴실/외출 선택")
                .setMessage("퇴실 또는 외출을 선택해주세요.")
                .setPositiveButton("퇴실", (dialog, which) -> handleExit())
                .setNegativeButton("외출", (dialog, which) -> handleAway())
                .setCancelable(false)
                .show();
    }

    private void handleExit() {
        sendBreakStatus(false);
    }

    private void handleAway() {
        sendBreakStatus(true);
    }

    private void sendBreakStatus(boolean isBreak) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String jwtToken = getSharedPreferences("auth", MODE_PRIVATE).getString("jwt_token", "");
                if (jwtToken.isEmpty()) {
                    mainHandler.post(() -> {
                        showToast("로그인이 필요합니다.");
                        finish();
                    });
                    return;
                }

                URL breakUrl = new URL(BASE_URL + BREAK_ENDPOINT);
                connection = (HttpURLConnection) breakUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setDoOutput(true);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                JSONObject requestBody = new JSONObject();
                requestBody.put("isBreak", isBreak);
                String jsonInputString = requestBody.toString();

                Log.d(TAG, "보내는 JSON: " + jsonInputString);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    // ✅ 정상 처리 (일반적인 성공 응답)
                    mainHandler.post(() -> {
                        showToast(isBreak ? "외출 처리되었습니다." : "퇴실 처리되었습니다.");
                        finish();
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finishAffinity();
                    });
                } else if (responseCode == 204 && !isBreak) {
                    // ✅ 퇴실 처리 되었지만 서버가 응답 본문을 주지 않는 경우 (정상임)
                    mainHandler.post(() -> {
                        showToast("퇴실 처리되었습니다.");
                        finish();
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finishAffinity();
                    });
                } else {
                    // ❗ 에러 응답일 경우 (400, 401, 403, 500 등)
                    String errorMessage = "처리 실패";
                    try {
                        String response = convertInputStreamToString(connection.getErrorStream());
                        if (response != null && !response.isEmpty()) {
                            JSONObject errorJson = new JSONObject(response);
                            if (errorJson.has("message")) {
                                errorMessage = errorJson.getString("message");
                            }
                        } else {
                            // ⚠️ 204처럼 응답이 비어있을 수도 있음 (여기서 다시 로그만 찍고 끝냄)
                            Log.e(TAG, "서버 응답이 비어 있습니다.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "에러 메시지 파싱 실패", e);
                    }

                    final String finalErrorMessage = errorMessage;
                    mainHandler.post(() -> {
                        showToast(finalErrorMessage);
                        finish();
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finishAffinity();
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "상태 전송 중 오류", e);
                mainHandler.post(() -> {
                    showToast("처리 중 오류가 발생했습니다.");
                    finish();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private void checkIfReserved() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String jwtToken = getSharedPreferences("auth", MODE_PRIVATE).getString("jwt_token", "");
                if (jwtToken.isEmpty()) {
                    mainHandler.post(() -> {
                        showToast("로그인이 필요합니다.");
                        finish();
                    });
                    return;
                }

                URL isReservedUrl = new URL(BASE_URL + "/qr/isReserved");
                connection = (HttpURLConnection) isReservedUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    InputStream inputStream = connection.getInputStream();
                    String response = convertInputStreamToString(inputStream).trim(); // ← 공백 제거
                    Log.d(TAG, "서버 응답: " + response);

                    boolean isReserved = response.equalsIgnoreCase("true");
                    Log.d(TAG, "예약 여부: " + isReserved);

                    if (isReserved) {
                        mainHandler.postDelayed(this::showExitOrAwayDialog, 1000);
                    } else {
                        mainHandler.postDelayed(() -> {
                            showToast("예약된 좌석이 없습니다. 종료합니다.");
                            finish();
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_HOME);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finishAffinity();
                        }, 1000);
                    }
                }
                else {
                    String errorMessage = "예약 정보 확인에 실패했습니다.";
                    try {
                        String errorResponse = convertInputStreamToString(connection.getErrorStream());
                        JSONObject errorJson = new JSONObject(errorResponse);
                        if (errorJson.has("message")) {
                            errorMessage = errorJson.getString("message");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "에러 메시지 파싱 실패", e);
                    }

                    final String finalErrorMessage = errorMessage;
                    mainHandler.post(() -> {
                        new AlertDialog.Builder(QrCodeShow.this)
                                .setTitle("오류")
                                .setMessage(finalErrorMessage)
                                .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                                .show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "예약 여부 확인 중 오류", e);
                mainHandler.post(() -> {
                    new AlertDialog.Builder(QrCodeShow.this)
                            .setTitle("오류")
                            .setMessage("예약 정보 확인 중 오류가 발생했습니다.")
                            .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                            .show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
