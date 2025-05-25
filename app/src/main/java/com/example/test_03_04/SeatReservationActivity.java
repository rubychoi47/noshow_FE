package com.example.test_03_04;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SeatReservationActivity extends AppCompatActivity {
    private static final String TAG = "SeatReservationActivity";
    private static final String BASE_URL = "https://www.noshow2025.shop/api";
    private static final String USER_INFO_ENDPOINT = "/qr/info";
    private static final String AUTH_PREF_NAME = "auth";
    private static final String JWT_TOKEN_KEY = "jwt_token";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private TextView textTimer;
    private TextView sortedTextTimer;

    private LinearLayout top5Container;
    private Button btnSortByTime, btnSortByExtension, btnSortByFavorite;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.seat_reservation);

        initializeViews();
        setupFloorButtons();
        fetchUserInfo();
        updateMySeatInfo(); // 내 좌석 정보 업데이트 시작

        setContentView(R.layout.seat_reservation);
        top5Container = findViewById(R.id.top5Container);
        btnSortByTime = findViewById(R.id.btnSortByTime);
        btnSortByExtension = findViewById(R.id.btnSortByExtension);
        btnSortByFavorite = findViewById(R.id.btnSortByFavorite);
        textTimer = findViewById(R.id.textTimer);
        //sortedTextTimer = findViewById(R.id.sortedTextTimer);


      //  btnSortByTime.setOnClickListener(v -> fetchTop5ByRemainingTime());
        btnSortByExtension.setOnClickListener(v -> fetchTop5ByExtension());
        btnSortByFavorite.setOnClickListener(v -> fetchFavoriteSeats());
        textTimer.setOnClickListener(v -> showSeatStatusDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMySeatInfo(); // 앱 재시작 시 타이머 업데이트
    }

    private void initializeViews() {
        CustomCircularProgress circularProgress1 = findViewById(R.id.circularProgress1);
        circularProgress1.setProgress(30); // 2층 진행률 설정

        // 타이머 텍스트뷰 초기화
        textTimer = findViewById(R.id.textTimer);
        if (textTimer != null) {
            textTimer.setOnClickListener(v -> showSeatStatusDialog());
        }

        // QR 코드 보기 버튼 설정
        Button qrShowButton = findViewById(R.id.qr_show_btn);
        if (qrShowButton != null) {
            qrShowButton.setOnClickListener(v -> {
                // 사용자 정보를 가져와서 QR 코드 화면으로 이동
                fetchUserInfo();
            });
        } else {
            Log.e(TAG, "QR 코드 보기 버튼을 찾을 수 없습니다");
        }
    }

    private void setupFloorButtons() {
        View floor2Layout = findViewById(R.id.circularProgress1);
        if (floor2Layout != null) {
            floor2Layout.setOnClickListener(v -> navigateToFloor(2));
        } else {
            Log.e(TAG, "2층 버튼을 찾을 수 없습니다");
        }
    }

    private void navigateToFloor(int floor) {
        Intent intent = new Intent(SeatReservationActivity.this, RealSeatReservation.class);
        intent.putExtra("FLOOR", floor);
        startActivity(intent);
    }

    private void fetchUserInfo() {
        Log.d(TAG, "사용자 정보 조회 시작");
        // 로딩 다이얼로그 표시
        showLoadingDialog();
        
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                Log.d(TAG, "Connection 생성 시도");
                final HttpURLConnection finalConnection = createConnection();
                connection = finalConnection;
                Log.d(TAG, "Connection 생성 성공, 서버 응답 처리 시작");
                handleServerResponse(finalConnection);
            } catch (Exception e) {
                Log.e(TAG, "사용자 정보 조회 중 오류 발생", e);
                e.printStackTrace();
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    new AlertDialog.Builder(SeatReservationActivity.this)
                        .setTitle("오류")
                        .setMessage("사용자 정보를 가져오는데 실패했습니다.\n다시 로그인해주세요.")
                        .setPositiveButton("확인", (dialog, which) -> {
                            dialog.dismiss();
                            finish();
                        })
                        .show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                    Log.d(TAG, "Connection 종료");
                }
            }
        });
    }

    private HttpURLConnection createConnection() throws IOException {
        Log.d(TAG, "Connection 생성 시작");
        URL url = new URL(BASE_URL + USER_INFO_ENDPOINT);
        Log.d(TAG, "요청 URL: " + url.toString());
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        
        // JWT 토큰 가져오기
        String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE)
                .getString(JWT_TOKEN_KEY, "");
        Log.d(TAG, "JWT 토큰 존재 여부: " + (!jwtToken.isEmpty() ? "있음" : "없음"));
        
        if (!jwtToken.isEmpty()) {
            Log.d(TAG, "JWT 토큰 길이: " + jwtToken.length());
            // 토큰 형식 확인
            if (!jwtToken.startsWith("Bearer ")) {
                jwtToken = "Bearer " + jwtToken;
            }
            connection.setRequestProperty("Authorization", jwtToken);
            Log.d(TAG, "Authorization 헤더 설정됨: " + jwtToken.substring(0, 20) + "...");
        } else {
            Log.e(TAG, "JWT 토큰이 없습니다.");
            throw new IOException("JWT 토큰이 없습니다.");
        }

        // 빈 요청 본문 전송 (POST 요청이므로)
        try (OutputStream os = connection.getOutputStream()) {
            JSONObject json = new JSONObject();
            byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
            os.flush();
        } catch (Exception e) {
            Log.e(TAG, "요청 본문 전송 중 오류", e);
            throw new IOException("요청 본문 전송 실패", e);
        }
        
        Log.d(TAG, "Connection 생성 완료");
        return connection;
    }

    private void handleServerResponse(HttpURLConnection connection) throws IOException {
        Log.d(TAG, "서버 응답 처리 시작");
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "서버 응답 코드: " + responseCode);
        
        InputStream inputStream = null;
        String response = null;
        
        try {
            inputStream = (responseCode >= 200 && responseCode < 300) ?
                    connection.getInputStream() : connection.getErrorStream();
            
            if (inputStream != null) {
                response = convertInputStreamToString(inputStream);
                Log.d(TAG, "서버 응답 내용: " + response);
            } else {
                Log.e(TAG, "서버 응답 스트림이 null입니다.");
                response = ""; // 빈 문자열로 처리
            }
        } catch (IOException e) {
            Log.e(TAG, "서버 응답 읽기 중 오류", e);
            throw e; // 예외 다시 던지기
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "응답 스트림 닫기 중 오류", e);
                }
            }
        }
        
        if (responseCode == 401) {
            Log.e(TAG, "인증 실패 (401): " + response);
            handleAuthError("로그인이 만료되었습니다. 다시 로그인해주세요.");
        } else if (responseCode == 403) {
            Log.e(TAG, "권한 없음 (403): " + response);
            handleAuthError("접근 권한이 없습니다. 다시 로그인해주세요.");
        } else if (responseCode >= 400) { // 4xx 이상의 클라이언트/서버 오류
            Log.e(TAG, "클라이언트/서버 오류 응답: " + responseCode + ", 응답: " + response);
            mainHandler.post(() -> {
                dismissLoadingDialog();
                showToast("서버 오류 발생: " + responseCode);
            });
        } else {
            processServerResponse(responseCode, response);
        }
    }

    private void handleAuthError(String message) {
        // SharedPreferences에서 JWT 토큰 삭제
        getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE)
            .edit()
            .remove(JWT_TOKEN_KEY)
            .apply();
        
        mainHandler.post(() -> {
            new AlertDialog.Builder(SeatReservationActivity.this)
                .setTitle("오류")
                .setMessage(message)
                .setPositiveButton("확인", (dialog, which) -> {
                    dialog.dismiss();
                    // 로그인 화면으로 이동
                    Intent intent = new Intent(SeatReservationActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
        });
    }

    private AlertDialog loadingDialog;

    private void showLoadingDialog() {
        mainHandler.post(() -> {
            if (loadingDialog == null || !loadingDialog.isShowing()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setView(R.layout.seat_reservation);
                builder.setCancelable(false);
                loadingDialog = builder.create();
                loadingDialog.show();
            }
        });
    }

    private void dismissLoadingDialog() {
        mainHandler.post(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    private void processServerResponse(int responseCode, String response) {
        Log.d(TAG, "서버 응답 처리 시작 - 코드: " + responseCode);
        Log.d(TAG, "처리할 응답 내용: " + response);
        
        // 204 NO_CONTENT 응답 처리
        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            Log.d(TAG, "서버 응답이 NO_CONTENT입니다. QR 코드 생성으로 진행합니다.");
            // SharedPreferences에서 저장된 사용자 정보 가져오기
            SharedPreferences prefs = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE);
            String email = prefs.getString("user_email", "");
            String studentId = prefs.getString("student_id", "");
            String name = prefs.getString("user_name", "");
            
            if (!email.isEmpty() && !studentId.isEmpty() && !name.isEmpty()) {
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    Log.d(TAG, "저장된 사용자 정보로 QR 코드 화면으로 이동");
                    navigateToQrCodeShow(email, studentId, name, 0);
                });
            } else {
                Log.e(TAG, "저장된 사용자 정보가 없습니다. 이메일: " + email + ", 학번: " + studentId + ", 이름: " + name);
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    showToast("사용자 정보를 찾을 수 없습니다.");
                });
            }
            return;
        }

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try {
                Log.d(TAG, "JSON 파싱 시도: " + response);
                if (response == null || response.trim().isEmpty()) {
                    Log.e(TAG, "응답 내용이 비어있거나 null입니다");
                    mainHandler.post(() -> {
                        dismissLoadingDialog();
                        showToast("서버 응답이 올바르지 않습니다");
                    });
                    return;
                }

                final JSONObject jsonResponse = new JSONObject(response);
                Log.d(TAG, "JSON 파싱 성공: " + jsonResponse.toString());
                
                // 필수 필드 확인
                if (!jsonResponse.has("email")) {
                    Log.e(TAG, "응답에 email 필드 없음");
                    mainHandler.post(() -> {
                        dismissLoadingDialog();
                        showToast("사용자 이메일 정보가 없습니다.");
                    });
                    return;
                }

                String email = jsonResponse.getString("email");
                String studentId = jsonResponse.optString("studentId", ""); // studentId가 없을 수도 있음
                String name = jsonResponse.optString("name", "");           // name이 없을 수도 있음
                int entry = jsonResponse.optInt("entry", -1);           // entry가 없을 수도 있음
                
                Log.d(TAG, "파싱된 정보 - 이메일: " + email + ", 학번: " + studentId + ", 이름: " + name + ", entry: " + entry);

                // 사용자 정보 SharedPreferences에 저장 (QR 코드 화면에서 사용)
                getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).edit()
                    .putString("user_email", email)
                    .putString("student_id", studentId)
                    .putString("user_name", name)
                    .apply();
                Log.d(TAG, "사용자 정보 SharedPreferences에 저장 완료");
                
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    navigateToQrCodeShow(email, studentId, name, entry);
                });

            } catch (org.json.JSONException e) {
                Log.e(TAG, "JSON 파싱 오류: " + response, e);
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    showToast("서버 응답 형식 오류");
                });
            } catch (Exception e) {
                Log.e(TAG, "서버 응답 처리 중 예상치 못한 오류", e);
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    showToast("사용자 정보 처리 중 오류");
                });
            }
        } else {
            Log.e(TAG, "HTTP 오류 응답: " + responseCode + ", 응답: " + response);
            mainHandler.post(() -> {
                dismissLoadingDialog();
                showToast("서버 통신 오류: " + responseCode);
            });
        }
    }

    private void navigateToQrCodeShow(String email, String studentId, String name, int entry) {
        mainHandler.post(() -> {
            Intent intent = new Intent(SeatReservationActivity.this, QrCodeShow.class);
            intent.putExtra("email", email);
            intent.putExtra("studentId", studentId);
            intent.putExtra("name", name);
            intent.putExtra("entry", entry);
            startActivity(intent);
        });
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(SeatReservationActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    // 내 좌석 정보 업데이트 메서드
    private void updateMySeatInfo() {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) return;

                final URL statusUrl = new URL(BASE_URL + "/seat/status");
                connection = (HttpURLConnection) statusUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    String response = convertInputStreamToString(connection.getInputStream());
                    JSONObject jsonResponse = new JSONObject(response);
                    
                    if (jsonResponse.has("seatId")) {
                        String seatId = jsonResponse.getString("seatId");
                        long remainingMinutes = jsonResponse.optLong("remainingMinutes", 0);
                        int numOfExtensions = jsonResponse.optInt("numOfExtensions", 0);
                        int seatNumber = Integer.parseInt(seatId);
                        
                        mainHandler.post(() -> updateTimer(remainingMinutes, numOfExtensions, seatNumber));
                    } else {
                        // 좌석 정보가 없는 경우 메시지 표시
                        mainHandler.post(() -> {
                            if (textTimer != null) {
                                textTimer.setText("이용정보가 없습니다.");
                            }
                        });
                    }
                } else {
                     // 오류 발생 시 메시지 표시 (선택 사항)
                     mainHandler.post(() -> {
                         if (textTimer != null) {
                             textTimer.setText("좌석 정보를 가져오는데 실패했습니다.");
                         }
                     });
                }
            } catch (Exception e) {
                Log.e(TAG, "내 좌석 정보 업데이트 중 오류", e);
                mainHandler.post(() -> {
                    if (textTimer != null) {
                        textTimer.setText("오류 발생");
                    }
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // 타이머 업데이트 메서드
    private void updateTimer(long remainingMinutes, int numOfExtensions, int seatNumber) {
        if (textTimer != null) {
            String timerMessage = String.format("%d번 좌석 | 남은 시간: %s | 연장 가능 횟수: %d회", 
                seatNumber, formatTime(remainingMinutes), numOfExtensions);
            textTimer.setText(timerMessage);
        }
    }

    // 시간 포맷 메서드
    private String formatTime(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    // 좌석 상태 다이얼로그 표시
    private void showSeatStatusDialog() {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) {
                    mainHandler.post(() -> showToast("로그인이 필요합니다."));
                    return;
                }

                final URL statusUrl = new URL(BASE_URL + "/seat/status");
                connection = (HttpURLConnection) statusUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    String response = convertInputStreamToString(connection.getInputStream());
                    JSONObject jsonResponse = new JSONObject(response);
                    
                    if (jsonResponse.has("seatId")) {
                        String seatId = jsonResponse.getString("seatId");
                        long remainingMinutes = jsonResponse.optLong("remainingMinutes", 0);
                        int numOfExtensions = jsonResponse.optInt("numOfExtensions", 0);
                        
                        mainHandler.post(() -> {
                            new AlertDialog.Builder(SeatReservationActivity.this)
                                    .setTitle("내 좌석 정보")
                                    .setMessage(String.format("좌석 번호: %s\n남은 시간: %s\n연장 가능 횟수: %d회\n\n무엇을 하시겠습니까?", 
                                        seatId, formatTime(remainingMinutes), numOfExtensions))
                                    .setPositiveButton("연장", (dialog, which) -> {
                                        sendExtendRequest();
                                        dialog.dismiss();
                                    })
                                    .setNegativeButton("반납", (dialog, which) -> {
                                        sendReturnRequest();
                                        dialog.dismiss();
                                    })
                                    .setNeutralButton("취소", (dialog, which) -> dialog.dismiss())
                                    .show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 상태 확인 중 오류", e);
                mainHandler.post(() -> showToast("좌석 정보를 가져오는데 실패했습니다."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // 좌석 연장 요청
    private void sendExtendRequest() {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) {
                    mainHandler.post(() -> showToast("로그인이 필요합니다."));
                    return;
                }

                final URL url = new URL(BASE_URL + "/reservation/extend");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    mainHandler.post(() -> {
                        showToast("좌석이 연장되었습니다.");
                        updateMySeatInfo(); // 좌석 정보 갱신
                    });
                } else {
                    mainHandler.post(() -> showToast("좌석 연장에 실패했습니다."));
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 연장 중 오류", e);
                mainHandler.post(() -> showToast("좌석 연장 중 오류가 발생했습니다."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // 좌석 반납 요청
    private void sendReturnRequest() {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) {
                    mainHandler.post(() -> showToast("로그인이 필요합니다."));
                    return;
                }

                final URL url = new URL(BASE_URL + "/reservation/exit");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    mainHandler.post(() -> {
                        showToast("좌석이 반납되었습니다.");
                        if (textTimer != null) {
                            textTimer.setText(""); // 타이머 텍스트 초기화
                        }
                    });
                } else {
                    mainHandler.post(() -> showToast("좌석 반납에 실패했습니다."));
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 반납 중 오류", e);
                mainHandler.post(() -> showToast("좌석 반납 중 오류가 발생했습니다."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
   /*
    //SeatTop5DisplayActivity에 있던 것들
    private void fetchTop5ByRemainingTime() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://your-api-url.com/seats/remaining-time")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonString = response.body().string();
                    try {
                        JSONArray seatArray = new JSONArray(jsonString);
                        showSeatList(seatArray);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
*/
    private void fetchTop5ByExtension() {
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seats/remainingNumOfExtension")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONArray jsonArray = new JSONArray(response.body().string());
                    List<SeatReservationActivity.SeatRemainingNumOfExtensionResponse> list = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        list.add(new SeatReservationActivity.SeatRemainingNumOfExtensionResponse(
                                obj.getString("seatId"),
                                obj.getInt("numOfExtensions"),
                                obj.getLong("remainingMinutes")
                        ));
                    }
                    list.sort(Comparator.comparingInt((SeatReservationActivity.SeatRemainingNumOfExtensionResponse s) -> s.numOfExtensions)
                            .thenComparingLong(s -> s.remainingMinutes));
                    List<SeatReservationActivity.SeatRemainingNumOfExtensionResponse> top5 = list.subList(0, Math.min(5, list.size()));
                    runOnUiThread(() -> updateTop5TextByExtension(top5));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void fetchFavoriteSeats() {
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/favorite")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SeatReservationActivity.this, "서버 요청 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONArray jsonArray = new JSONArray(response.body().string());
                    List<String> seatIds = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        seatIds.add(jsonArray.getJSONObject(i).getString("seatId"));
                    }
                    runOnUiThread(() -> updateFavoriteSeats(seatIds));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void updateTop5Text(List<SeatReservationActivity.SeatRemainingTimeResponse> list) {
        top5Container.removeAllViews();
        for (SeatReservationActivity.SeatRemainingTimeResponse item : list) {
            TextView tv = new TextView(this);
            tv.setText("좌석 " + item.seatId + " - " + item.remainingMinutes + "분 남음");
            tv.setTextSize(16f);
            top5Container.addView(tv);
        }
    }

    private void updateTop5TextByExtension(List<SeatReservationActivity.SeatRemainingNumOfExtensionResponse> list) {
        top5Container.removeAllViews();
        for (SeatReservationActivity.SeatRemainingNumOfExtensionResponse item : list) {
            TextView tv = new TextView(this);
            tv.setText("좌석 " + item.seatId + " - " + item.remainingMinutes + "분 남음 / 연장 " + item.numOfExtensions + "회");
            tv.setTextSize(16f);
            top5Container.addView(tv);
        }
    }

    private void updateFavoriteSeats(List<String> seatIds) {
        top5Container.removeAllViews();
        for (String seatId : seatIds) {
            TextView tv = new TextView(this);
            tv.setText("선호 좌석: " + seatId);
            tv.setTextSize(16f);
            top5Container.addView(tv);
        }
    }

    private void showSeatDialog(String seatId, long remainingMinutes, int numOfExtensions) {
        new AlertDialog.Builder(this)
                .setTitle("나의 좌석 정보")
                .setMessage("좌석 ID: " + seatId +
                        "\n남은 시간: " + remainingMinutes + "분" +
                        "\n연장 횟수: " + numOfExtensions + "회")
                .setPositiveButton("반납", (dialog, which) -> sendReturnRequest())
                .setNegativeButton("연장", (dialog, which) -> sendExtendRequest())
                .setNeutralButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }
    static class SeatRemainingTimeResponse {
        String seatId;
        long remainingMinutes;
        SeatRemainingTimeResponse(String seatId, long remainingMinutes) {
            this.seatId = seatId;
            this.remainingMinutes = remainingMinutes;
        }
    }

    static class SeatRemainingNumOfExtensionResponse {
        String seatId;
        int numOfExtensions;
        long remainingMinutes;
        SeatRemainingNumOfExtensionResponse(String seatId, int numOfExtensions, long remainingMinutes) {
            this.seatId = seatId;
            this.numOfExtensions = numOfExtensions;
            this.remainingMinutes = remainingMinutes;
        }
    }
}