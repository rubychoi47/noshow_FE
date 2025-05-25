package com.example.test_03_04;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EnterStudentIdActivity extends AppCompatActivity {
    private static final String TAG = "EnterStudentIdActivity";
    private static final String BASE_URL = "https://www.noshow2025.shop/api";
    private static final String REGISTER_ENDPOINT = "/auth/register";
    private static final String AUTH_PREF_NAME = "auth";
    private static final String JWT_TOKEN_KEY = "jwt_token";
    private static final String USER_EMAIL_KEY = "user_email";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    private EditText editTextStudentEmail;
    private EditText editTextStudentId;
    private EditText editTextStudentName;
    private Button buttonSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_student_id);

        initializeViews();
        setupEmailFromIntent();
        setupSubmitButton();
    }

    private void initializeViews() {
        editTextStudentEmail = findViewById(R.id.editTextStudentEmail);
        editTextStudentId = findViewById(R.id.editTextStudentId);
        editTextStudentName = findViewById(R.id.editTextStudentName);
        buttonSubmit = findViewById(R.id.submitStudentIdButton);
    }

    private void setupEmailFromIntent() {
        String emailFromIntent = getIntent().getStringExtra("email");
        if (emailFromIntent != null) {
            editTextStudentEmail.setText(emailFromIntent);
        }
    }

    private void setupSubmitButton() {
        buttonSubmit.setOnClickListener(v -> validateAndSubmit());
    }

    private void validateAndSubmit() {
        String email = editTextStudentEmail.getText().toString().trim();
        String studentId = editTextStudentId.getText().toString().trim();
        String name = editTextStudentName.getText().toString().trim();

        if (!validateInputs(email, studentId, name)) {
            return;
        }

        Log.d(TAG, "입력 정보 - 이메일: " + email + ", 학번: " + studentId + ", 이름: " + name);
        submitStudentInfo(email, studentId, name);
    }

    private boolean validateInputs(String email, String studentId, String name) {
        if (email.isEmpty() || studentId.isEmpty() || name.isEmpty()) {
            showToast("모든 항목을 입력하세요.");
            return false;
        }
        return true;
    }

    private void submitStudentInfo(String email, String studentId, String name) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = createConnection();
                sendStudentInfo(connection, email, studentId, name);
                handleServerResponse(connection);
            } catch (Exception e) {
                Log.e(TAG, "서버 통신 오류", e);
                showToast("서버 통신 중 오류가 발생했습니다.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private HttpURLConnection createConnection() throws IOException {
        URL url = new URL(BASE_URL + REGISTER_ENDPOINT);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        return connection;
    }

    private void sendStudentInfo(HttpURLConnection connection, String email, String studentId, String name) throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("studentId", studentId);
            json.put("name", name);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "JSON 생성 중 오류 발생", e);
            throw new IOException("JSON 데이터 생성 실패", e);
        }
    }

    private void handleServerResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        InputStream inputStream = (responseCode >= 200 && responseCode < 300) ?
                connection.getInputStream() : connection.getErrorStream();
        
        String response = convertInputStreamToString(inputStream);
        processServerResponse(responseCode, response);
    }

    private void processServerResponse(int responseCode, String response) {
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try {
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.has("accessToken")) {
                    String accessToken = jsonResponse.getString("accessToken");
                    String email = editTextStudentEmail.getText().toString();
                    saveAuthData(accessToken, email);
                    navigateToSeatReservation();
                } else {
                    showToast("서버 응답 형식이 올바르지 않습니다.");
                }
            } catch (Exception e) {
                Log.e(TAG, "응답 데이터 파싱 오류", e);
                showToast("서버 응답 처리 중 오류가 발생했습니다.");
            }
        } else {
            showToast("등록 실패: " + responseCode);
        }
    }

    private void saveAuthData(String jwt, String email) {
        getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(JWT_TOKEN_KEY, jwt)
                .putString(USER_EMAIL_KEY, email)
                .apply();
        Log.d(TAG, "인증 데이터 저장 완료 - 이메일: " + email);
    }

    private void navigateToSeatReservation() {
        mainHandler.post(() -> {
            showToast("등록이 완료되었습니다!");
            startActivity(new Intent(EnterStudentIdActivity.this, SeatReservationActivity.class));
            finish();
        });
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

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(EnterStudentIdActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}