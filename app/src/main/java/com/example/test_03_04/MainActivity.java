package com.example.test_03_04;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

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
import java.util.concurrent.TimeUnit;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BASE_URL = "https://www.noshow2025.shop/api";
    private static final String AUTH_ENDPOINT = "/auth/login";
    private static final String AUTH_PREF_NAME = "auth";
    private static final String JWT_TOKEN_KEY = "jwt_token";
    private static final String USER_EMAIL_KEY = "user_email";
    private static final int CONNECT_TIMEOUT = 10000; // 10초로 단축
    private static final int READ_TIMEOUT = 10000;    // 10초로 단축
    private static final int INIT_DELAY_MS = 100;     // 초기화 지연 시간 조정
    private static final int MAX_RETRY_COUNT = 3;     // 최대 재시도 횟수
    private static final int RETRY_DELAY_MS = 1000;   // 재시도 간격

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newFixedThreadPool(3); // 스레드 풀 크기 증가
    private volatile GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleLoginLauncher;
    private volatile boolean isInitialized = false;
    private ImageButton googleLoginButton;
    private SharedPreferences authPrefs;
    private ANRWatchDog anrWatchDog;
    private OkHttpClient httpClient;  // OkHttpClient 인스턴스 추가

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Google 로그인 런처는 onCreate에서 먼저 초기화
        setupGoogleLoginLauncher();
        
        // OkHttpClient 초기화
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build();
        
        // SharedPreferences 미리 초기화
        authPrefs = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE);
        
        // UI 초기화를 최우선으로
        setContentView(R.layout.login);
        setupBasicUI();
        
        // 나머지 초기화는 지연 실행
        mainHandler.postDelayed(this::initializeApp, INIT_DELAY_MS);
    }

    private void initializeApp() {
        if (isInitialized) return;
        
        executorService.execute(() -> {
            try {
                // ANR 감지기 초기화
                initializeANRWatchDog();
                
                // Google 서비스 초기화는 실제 필요할 때까지 지연
                if (googleSignInClient == null) {
                    mainHandler.post(() -> {
                        try {
                            googleSignInClient = getGoogleClient();
                        } catch (Exception e) {
                            Log.e(TAG, "Google 클라이언트 초기화 실패", e);
                        }
                    });
                }
                
                isInitialized = true;
                Log.d(TAG, "앱 초기화 완료");
            } catch (Exception e) {
                Log.e(TAG, "앱 초기화 실패", e);
                mainHandler.post(() -> showToast("앱 초기화 중 오류가 발생했습니다"));
            }
        });
    }

    private void initializeANRWatchDog() {
        try {
            if (anrWatchDog != null) {
                anrWatchDog.interrupt();
            }
            
            anrWatchDog = new ANRWatchDog();
            anrWatchDog.start();
            Log.d(TAG, "ANR 감지기 초기화 완료 (기본 타임아웃: 5초)");
        } catch (Exception e) {
            Log.e(TAG, "ANR 감지기 초기화 실패", e);
        }
    }

    private void handleGoogleLoginClick() {
        if (!isInitialized) {
            showToast("앱 초기화 중입니다. 잠시만 기다려주세요.");
            return;
        }

        executorService.execute(() -> {
            try {
                if (googleSignInClient == null) {
                    googleSignInClient = getGoogleClient();
                }
                signInWithGoogle();
            } catch (Exception e) {
                Log.e(TAG, "Google 로그인 처리 실패", e);
                mainHandler.post(() -> showToast("Google 로그인 처리 중 오류가 발생했습니다"));
            }
        });
    }

    private GoogleSignInClient getGoogleClient() {
        try {
            GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(getString(R.string.google_client_ID))
                    .build();
            return GoogleSignIn.getClient(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Google 클라이언트 생성 실패", e);
            throw e;
        }
    }

    private void signInWithGoogle() {
        if (googleSignInClient == null) {
            Log.e(TAG, "googleSignInClient 초기화 실패");
            mainHandler.post(() -> showToast("Google 로그인 클라이언트 초기화 실패"));
            return;
        }

        try {
            googleSignInClient.signOut()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "이전 로그인 정보 로그아웃 완료");
                        mainHandler.post(this::launchGoogleSignIn);
                    } else {
                        Log.e(TAG, "로그아웃 실패");
                        mainHandler.post(() -> showToast("이전 로그인 정보 초기화 실패"));
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Google 로그인 처리 중 오류", e);
            mainHandler.post(() -> showToast("Google 로그인 처리 중 오류 발생"));
        }
    }

    private void launchGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleLoginLauncher.launch(signInIntent);
        Log.d(TAG, "Google 로그인 Intent 발사");
    }

    private void setupGoogleLoginLauncher() {
        try {
            googleLoginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleGoogleSignInResult
            );
            Log.d(TAG, "Google 로그인 런처 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "Google 로그인 런처 초기화 실패", e);
        }
    }

    private void handleGoogleSignInResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            Intent data = result.getData();
            if (data == null) {
                Log.e(TAG, "Intent가 null입니다");
                return;
            }

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            task.addOnCompleteListener(this::handleGoogleSignInTask);
        } else {
            Log.e(TAG, "로그인 실패 또는 취소됨. resultCode=" + result.getResultCode());
            showToast("로그인이 취소되었습니다.");
        }
    }

    private void handleGoogleSignInTask(@NonNull Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                String idToken = account.getIdToken();
                if (idToken != null) {
                    Log.d(TAG, "ID 토큰 획득 성공");
                    sendGoogleLoginRequest(idToken);
                } else {
                    Log.e(TAG, "ID 토큰이 null입니다");
                    showToast("로그인 토큰을 가져오는데 실패했습니다.");
                }
            }
        } catch (ApiException e) {
            handleGoogleSignInError(e);
        }
    }

    private void handleGoogleSignInError(ApiException e) {
        Log.e(TAG, "Google 로그인 실패: code=" + e.getStatusCode(), e);
        String errorMessage = getGoogleSignInErrorMessage(e.getStatusCode());
        showToast(errorMessage);
    }

    private String getGoogleSignInErrorMessage(int statusCode) {
        switch (statusCode) {
            case 7: return "네트워크 연결을 확인해주세요.";
            case 12500: return "Google 로그인 설정이 잘못되었습니다. 개발자에게 문의하세요.";
            case 12501: return "로그인이 취소되었습니다.";
            case 12502: return "네트워크 연결이 불안정합니다.";
            default: return "Google 로그인 실패: " + statusCode;
        }
    }

    private void sendGoogleLoginRequest(String idToken) {
        executorService.execute(() -> {
            int retryCount = 0;
            while (retryCount < MAX_RETRY_COUNT) {
                try {
                    Request request = createLoginRequest(idToken);
                    try (Response response = httpClient.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            processServerResponse(response.code(), responseBody);
                            return;
                        } else {
                            Log.e(TAG, "서버 응답 실패: " + response.code());
                            if (response.code() >= 500) {
                                retryCount++;
                                if (retryCount < MAX_RETRY_COUNT) {
                                    Thread.sleep(RETRY_DELAY_MS);
                                    continue;
                                }
                            }
                            mainHandler.post(() -> showToast("서버 오류 발생: " + response.code()));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "서버 통신 오류", e);
                    retryCount++;
                    if (retryCount < MAX_RETRY_COUNT) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                            continue;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    mainHandler.post(() -> showToast("서버 통신 실패"));
                }
                break;
            }
        });
    }

    private Request createLoginRequest(String idToken) throws IOException {
        SharedPreferences prefs = getSharedPreferences("fcm", MODE_PRIVATE);
        String fcmToken = prefs.getString("fcm_token", null);

        JSONObject json = new JSONObject();
        try {
            json.put("idToken", idToken);
            if (fcmToken != null) {
                json.put("fcmToken", fcmToken);
                Log.d(TAG, "서버에 전송할 FCM 토큰: " + fcmToken);
            }
        } catch (JSONException e) {
            throw new IOException("JSON 생성 실패", e);
        }

        return new Request.Builder()
            .url(BASE_URL + AUTH_ENDPOINT)
            .post(RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
            ))
            .build();
    }

    private void processServerResponse(int responseCode, String response) {
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try {
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.has("accessToken")) {
                    String accessToken = jsonResponse.getString("accessToken");
                    String email = jsonResponse.optString("email", "");
                    final int entry = jsonResponse.optInt("entry", 0);
                    
                    saveAuthData(accessToken, email);
                    
                    navigateToSeatReservation();
                } else if (jsonResponse.has("reason")) {
                    String reason = jsonResponse.getString("reason");
                    if (reason != null) {
                        handleLoginReason(reason);
                    } else {
                        Log.e(TAG, "reason이 null입니다");
                        showToast("로그인 실패: 알 수 없는 이유");
                    }
                } else {
                    Log.e(TAG, "응답에 필수 필드가 없습니다: " + response);
                    showToast("서버 응답 형식이 올바르지 않습니다");
                }
            } catch (org.json.JSONException e) {
                Log.e(TAG, "JSON 파싱 오류: " + response, e);
                showToast("서버 응답 처리 중 오류가 발생했습니다");
            } catch (Exception e) {
                Log.e(TAG, "응답 처리 중 예상치 못한 오류", e);
                showToast("서버 응답 처리 중 오류가 발생했습니다");
            }
        } else {
            Log.e(TAG, "HTTP 오류 응답: " + responseCode + ", 응답: " + response);
            showToast("서버 응답 실패: " + responseCode);
        }
    }

    private void handleLoginReason(String reason) {
        if ("USER_NOT_FOUND".equals(reason)) {
            navigateToEnterStudentId();
        } else {
            showToast("로그인 실패: " + reason);
        }
    }

    private void navigateToSeatReservation() {
        mainHandler.post(() -> {
            showToast("로그인 성공!");
            startActivity(new Intent(MainActivity.this, SeatReservationActivity.class));
            finish();
        });
    }

    private void navigateToEnterStudentId() {
        mainHandler.post(() -> {
            startActivity(new Intent(MainActivity.this, EnterStudentIdActivity.class));
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

    private void saveAuthData(String jwt, String email) {
        try {
            authPrefs.edit()
                .putString(JWT_TOKEN_KEY, jwt)
                .putString(USER_EMAIL_KEY, email)
                .apply();
            Log.d(TAG, "인증 데이터 저장 완료 - 이메일: " + email);
        } catch (Exception e) {
            Log.e(TAG, "인증 데이터 저장 실패", e);
        }
    }

    private void showToast(String message) {
        if (!isFinishing() && !isDestroyed()) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // ANR 감지기 정리
            if (anrWatchDog != null) {
                anrWatchDog.interrupt();
                anrWatchDog = null;
            }
            
            // 네트워크 리소스 정리는 백그라운드 스레드에서 처리
            executorService.execute(() -> {
                try {
                    if (httpClient != null) {
                        httpClient.dispatcher().executorService().shutdown();
                        httpClient.connectionPool().evictAll();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "HTTP 클라이언트 정리 중 오류", e);
                }
            });
            
            // ExecutorService 정리
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            Log.e(TAG, "리소스 정리 중 오류", e);
        }
    }

    private void setupBasicUI() {
        try {
            // UI 초기화를 최적화
            View rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                rootView.post(() -> {
                    try {
                        googleLoginButton = findViewById(R.id.googleLoginButton);
                        if (googleLoginButton != null) {
                            googleLoginButton.setOnClickListener(v -> {
                                if (!isInitialized) {
                                    showToast("앱 초기화 중입니다. 잠시만 기다려주세요.");
                                    return;
                                }
                                Log.d(TAG, "구글 로그인 버튼 클릭");
                                handleGoogleLoginClick();
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "기본 UI 설정 실패", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "기본 UI 설정 실패", e);
        }
    }
}