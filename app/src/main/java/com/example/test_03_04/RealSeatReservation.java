package com.example.test_03_04;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RealSeatReservation extends AppCompatActivity {

    private static final String TAG = "RealSeatReservation";
    private static final String BASE_URL = "https://www.noshow2025.shop/api";
    private static final String AUTH_PREF_NAME = "auth";
    private static final String JWT_TOKEN_KEY = "jwt_token";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private LinearLayout seatLayout;
    private int currentFloor;
    private String mySeatId;
    private Set<String> reservedSeats = new HashSet<>();
    private Map<String, SeatStatus> seatStatusMap = new HashMap<>();
    private AlertDialog loadingDialog;
    private ImageButton buttonBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat);

        currentFloor = getIntent().getIntExtra("FLOOR", 2);
        seatLayout = findViewById(R.id.seatContainer);
        if (seatLayout == null) {
            Log.e(TAG, "seatLayout을 찾을 수 없습니다");
            return;
        }

        buttonBack = findViewById(R.id.buttonback);
        if (buttonBack != null) {
            buttonBack.setOnClickListener(v -> finish());
        } else {
            Log.e(TAG, "buttonback ImageButton을 찾을 수 없습니다.");
        }

        initializeSeatData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeSeatData();
    }

    private void initializeSeatData() {
        Log.d(TAG, "좌석 데이터 초기화 시작");
        showLoadingDialog();
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) {
                    Log.e(TAG, "JWT 토큰이 없습니다.");
                    mainHandler.post(() -> {
                        dismissLoadingDialog();
                        showToast("로그인이 필요합니다.");
                        finish();
                    });
                    return;
                }

                // 먼저 내 좌석 정보 가져오기
                final URL statusUrl = new URL(BASE_URL + "/seat/status");
                Log.d(TAG, "내 좌석 상태 API 호출: " + statusUrl.toString());
                
                connection = (HttpURLConnection) statusUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "내 좌석 상태 API 응답 코드: " + responseCode);
                
                if (responseCode == 200) {
                    String response = convertInputStreamToString(connection.getInputStream());
                    Log.d(TAG, "내 좌석 상태 API 응답: " + response);
                    
                    JSONObject jsonResponse = new JSONObject(response);
                    if (jsonResponse.has("seatId") && !jsonResponse.isNull("seatId")) {
                        mySeatId = jsonResponse.getString("seatId");
                        Log.d(TAG, "내 좌석 ID 업데이트: " + mySeatId);
                    } else {
                        mySeatId = null;
                        Log.d(TAG, "내 좌석이 없습니다.");
                    }
                }

                // 그 다음 모든 예약된 좌석 정보 가져오기
                final URL reservedUrl = new URL(BASE_URL + "/seat/reserved");
                Log.d(TAG, "예약된 좌석 목록 API 호출: " + reservedUrl.toString());
                
                connection = (HttpURLConnection) reservedUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                responseCode = connection.getResponseCode();
                Log.d(TAG, "예약된 좌석 목록 API 응답 코드: " + responseCode);
                
                if (responseCode == 200) {
                    String response = convertInputStreamToString(connection.getInputStream());
                    Log.d(TAG, "예약된 좌석 목록 API 응답: " + response);
                    
                    JSONArray jsonArray = new JSONArray(response);
                    reservedSeats.clear();
                    seatStatusMap.clear();
                    Log.d(TAG, "---------------------0");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String seat = jsonArray.getString(i);
                        reservedSeats.add(seat);

                    }
                }

                mainHandler.post(() -> {
                    Log.d(TAG, "좌석 레이아웃 업데이트 시작");
                    updateSeatLayout();
                });

            } catch (Exception e) {
                Log.e(TAG, "좌석 데이터 초기화 중 오류", e);
                mainHandler.post(() -> {
                    dismissLoadingDialog();
                    showToast("좌석 정보를 가져오는데 실패했습니다. 1");
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void updateSeatLayout() {
        Log.d(TAG, "updateSeatLayout: 시작 - 예약된 좌석 수: " + reservedSeats.size());
        Log.d(TAG, "예약된 좌석 목록: " + reservedSeats.toString());
        
        // 기존 레이아웃 제거
        seatLayout.removeAllViews();
        
        // 새로운 레이아웃 생성
        createSeatLayout();
        
        // 로딩 다이얼로그 닫기
        dismissLoadingDialog();
        
        Log.d(TAG, "updateSeatLayout: 완료");
    }

    private void createSeatLayout() {
        Log.d(TAG, "createSeatLayout: 시작");
        // 총 좌석 수와 한 줄당 좌석 수 설정
        int totalSeats = 180;
        int seatsPerRow = 10;

        // 창가석 번호 설정
        Set<Integer> windowSeats = new HashSet<>();
        for (int i = 1; i <= 25; i++) {
            windowSeats.add(i);
        }
        for (int i = 99; i <= 106; i++) {
            windowSeats.add(i);
        }
        for (int i = 148; i <= 157; i++) {
            windowSeats.add(i);
        }

        // 기존 레이아웃 초기화
        seatLayout.removeAllViews();
        
        // 좌석 레이아웃 생성
        LinearLayout currentRow = null;
        for (int i = 1; i <= totalSeats; i++) {
            // 새로운 행 시작
            if ((i - 1) % seatsPerRow == 0) {
                currentRow = new LinearLayout(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                rowParams.gravity = Gravity.CENTER;
                currentRow.setLayoutParams(rowParams);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                seatLayout.addView(currentRow);
            }

            // 좌석 버튼 생성
            if (currentRow != null) {
                boolean isWindowSeat = windowSeats.contains(i);
                Button seatButton = createSeatButton(i, isWindowSeat);
                
                // 좌석 버튼 레이아웃 파라미터 설정
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    dpToPx(48),
                    dpToPx(48)
                );
                buttonParams.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                seatButton.setLayoutParams(buttonParams);
                
                currentRow.addView(seatButton);
                
                String seatId = String.valueOf(i);
                Log.d(TAG, String.format("좌석 %d 생성 완료 - 예약됨: %b, 내 좌석: %b", 
                    i, reservedSeats.contains(seatId), seatId.equals(mySeatId)));
            }
        }
        Log.d(TAG, "createSeatLayout: 완료 - 총 " + totalSeats + "개 좌석 생성됨");
    }

    private Button createSeatButton(int seatNumber, boolean isWindowSeat) {
        Button seatButton = new Button(this);
        seatButton.setText(String.valueOf(seatNumber));
        seatButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        seatButton.setTextColor(Color.WHITE);
        seatButton.setGravity(Gravity.CENTER);
        seatButton.setAllCaps(false);
        seatButton.setPadding(0, 0, 0, 0);
        
        String seatId = String.valueOf(seatNumber);
        boolean isReserved = reservedSeats.contains(seatId);
        boolean isMySeat = seatId.equals(mySeatId);
        
        Log.d(TAG, String.format("좌석 %d 버튼 생성 - 예약됨: %b, 내 좌석: %b", 
            seatNumber, isReserved, isMySeat));
        
        // 좌석 상태에 따른 배경색 설정
        if (isReserved) {
            Log.d(TAG, String.format("좌석 %d: 예약된 좌석 배경색 설정 (seat_circle_gray)", seatNumber));
            seatButton.setBackgroundResource(R.drawable.seat_circle_gray);
            
            SeatStatus status = seatStatusMap.get(seatId);
            if (status != null) {
                String message = String.format("%d번 좌석\n남은 시간: %s\n연장 가능 횟수: %d회", 
                    seatNumber, formatTime(status.remainingMinutes), status.numOfExtensions);
                
                if (isMySeat) {
                    seatButton.setOnClickListener(v -> showMySeatDialog(seatNumber, seatButton));
                } else {
                    seatButton.setOnClickListener(v -> showReservedSeatDialog(message));
                }
            }
        } else {
            Log.d(TAG, String.format("좌석 %d: 빈 좌석 배경색 설정 (%s)", 
                seatNumber, isWindowSeat ? "seat_available_window" : "seat_available"));
            seatButton.setBackgroundResource(isWindowSeat ? 
                R.drawable.seat_available_window : R.drawable.seat_available);
            seatButton.setOnClickListener(v -> handleSeatClick(seatNumber, seatButton));
        }
        
        return seatButton;
    }

    private String formatTime(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    private void showMySeatDialog(int seatNumber, Button seatButton) {
        new AlertDialog.Builder(this)
            .setTitle("내 좌석 관리")
            .setMessage(String.format("%d번 좌석\n무엇을 하시겠습니까?", seatNumber))
            .setPositiveButton("연장", (dialog, which) -> {
                handleExtendSeat(seatNumber, seatButton);
                dialog.dismiss();
            })
            .setNegativeButton("반납", (dialog, which) -> {
                handleReturnSeat(seatNumber, seatButton);
                dialog.dismiss();
            })
            .setNeutralButton("취소", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void showReservedSeatDialog(String message) {
        new AlertDialog.Builder(this)
            .setTitle("예약된 좌석 정보")
            .setMessage(message)
            .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void handleSeatClick(int seatNumber, Button seatButton) {
        String clickedSeatId = String.valueOf(seatNumber);
        
        Log.d(TAG, String.format("좌석 클릭 - 번호: %d", seatNumber));

        // 먼저 예약 여부 확인
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) {
                    Log.e(TAG, "JWT 토큰이 없습니다.");
                    mainHandler.post(() -> showToast("로그인이 필요합니다."));
                    return;
                }

                // 예약 여부 확인
                final URL isReservedUrl = new URL(BASE_URL + "/reservation/isReserved");
                Log.d(TAG, "예약 여부 확인 API 호출: " + isReservedUrl.toString());
                
                connection = (HttpURLConnection) isReservedUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "예약 여부 확인 API 응답 코드: " + responseCode);
                
                if (responseCode == 200) {
                    String response = convertInputStreamToString(connection.getInputStream());
                    Log.d(TAG, "예약 여부 확인 API 응답: " + response);
                    //JSONObject jsonResponse = new JSONObject(response);
                    //boolean hasReservation = jsonResponse.optBoolean("isReserved", false);
                    boolean hasReservation = Boolean.parseBoolean(response.trim());
                    Log.d(TAG, "hasReservation: " + hasReservation);
                    if (hasReservation) {
                        // 이미 예약 중인 경우
                        Log.d(TAG, "이미 다른 좌석을 예약 중입니다.");
                        mainHandler.post(() -> {
                            new AlertDialog.Builder(RealSeatReservation.this)
                                .setTitle("예약 불가")
                                .setMessage("이미 다른 좌석을 예약 중입니다.\n현재 좌석을 반납한 후 다시 시도해주세요.")
                                .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                                .show();
                        });
                        return;
                    }

                    // 예약 중이 아닌 경우, 좌석 상태 확인
                    if (reservedSeats.contains(clickedSeatId)) {

                        // 좌석이 예약된 경우, 본인의 좌석인지 확인
                        final URL statusUrl = new URL(BASE_URL + "/seat/status");
                        Log.d(TAG, "좌석 상태 확인 API 호출: " + statusUrl.toString());
                        
                        connection = (HttpURLConnection) statusUrl.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                        connection.setConnectTimeout(CONNECT_TIMEOUT);
                        connection.setReadTimeout(READ_TIMEOUT);

                        responseCode = connection.getResponseCode();
                        Log.d(TAG, "좌석 상태 API 응답 코드: " + responseCode);
                        /*
                        if (responseCode == 200) {
                            response = convertInputStreamToString(connection.getInputStream());
                            Log.d(TAG, "좌석 상태 API 응답: " + response);
                            
                            jsonResponse = new JSONObject(response);
                            
                            if (jsonResponse.has("seatId") && !jsonResponse.isNull("seatId")) {
                                String mySeatId = jsonResponse.getString("seatId");
                                long remainingMinutes = jsonResponse.optLong("remainingMinutes", 0);
                                int numOfExtensions = jsonResponse.optInt("numOfExtensions", 0);
                                
                                Log.d(TAG, String.format("내 좌석 정보 - 번호: %s, 남은 시간: %d분, 연장 가능: %d회", 
                                    mySeatId, remainingMinutes, numOfExtensions));
                                
                                // 본인의 좌석인 경우
                                if (mySeatId.equals(clickedSeatId)) {
                                    Log.d(TAG, "본인의 좌석입니다.");
                                    mainHandler.post(() -> {
                                        new AlertDialog.Builder(RealSeatReservation.this)
                                            .setTitle("내 좌석 정보")
                                            .setMessage(String.format("%d번 좌석\n남은 시간: %s\n연장 가능 횟수: %d회\n\n무엇을 하시겠습니까?", 
                                                seatNumber, formatTime(remainingMinutes), numOfExtensions))
                                            .setPositiveButton("연장", (dialog, which) -> {
                                                handleExtendSeat(seatNumber, seatButton);
                                                dialog.dismiss();
                                            })
                                            .setNegativeButton("반납", (dialog, which) -> {
                                                handleReturnSeat(seatNumber, seatButton);
                                                dialog.dismiss();
                                            })
                                            .setNeutralButton("취소", (dialog, which) -> dialog.dismiss())
                                            .show();
                                    });
                                } else {
                                    Log.d(TAG, "다른 사람의 좌석입니다.");
                                    // 다른 사람의 좌석인 경우 seat/reserved API에서 가져온 정보 표시
                                    SeatStatus status = seatStatusMap.get(clickedSeatId);
                                    if (status != null) {
                                        String message = String.format("%d번 좌석\n남은 시간: %s\n연장 가능 횟수: %d회", 
                                            seatNumber, formatTime(status.remainingMinutes), status.numOfExtensions);
                                        mainHandler.post(() -> {
                                            new AlertDialog.Builder(RealSeatReservation.this)
                                                .setTitle("예약된 좌석 정보")
                                                .setMessage(message + "\n\n이미 예약된 좌석입니다.")
                                                .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                                                .show();
                                        });
                                    }
                                }
                            }
                        } else {

                            Log.d(TAG, "--------------------2");
                            String errorResponse = convertInputStreamToString(connection.getErrorStream());
                            Log.e(TAG, "좌석 상태 API 오류: " + responseCode + ", 응답: " + errorResponse);
                            mainHandler.post(() -> showToast("좌석 정보를 가져오는데 실패했습니다. 2"));
                        }*/
                    } else {
                        // 좌석이 예약되지 않은 경우, 예약 가능
                        Log.d(TAG, "예약 가능한 좌석입니다.");
                        mainHandler.post(() -> showSeatAssignmentDialog(seatNumber));
                    }
                } else {
                    String errorResponse = convertInputStreamToString(connection.getErrorStream());
                    Log.e(TAG, "예약 여부 확인 API 오류: " + responseCode + ", 응답: " + errorResponse);
                    mainHandler.post(() -> showToast("예약 상태 확인에 실패했습니다."));
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 상태 확인 중 오류", e);
                mainHandler.post(() -> showToast("좌석 정보를 가져오는데 실패했습니다. 3"));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void handleReturnSeat(int seatNumber, Button seatButton) {
        showLoadingDialog();
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
                connection.setRequestMethod("GET"); // 반납은 GET 요청으로 변경
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setDoOutput(false); // GET 요청이므로 본문 없음

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    mainHandler.post(() -> {
                        showToast("좌석 반납 완료.");
                        mySeatId = null; // 내 좌석 정보 초기화
                        initializeSeatData(); // 좌석 상태 새로고침
                    });
                } else {
                    String errorResponse = convertInputStreamToString(connection.getErrorStream());
                    Log.e(TAG, "좌석 반납 실패: " + responseCode + ", " + errorResponse);
                    mainHandler.post(() -> showToast("좌석 반납 실패: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 반납 중 오류", e);
                mainHandler.post(() -> showToast("좌석 반납 중 오류가 발생했습니다."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                mainHandler.post(this::dismissLoadingDialog);
            }
        });
    }

    private void handleExtendSeat(int seatNumber, Button seatButton) {
        showLoadingDialog();
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
                connection.setRequestMethod("GET"); // 연장은 GET 요청으로 변경
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setDoOutput(false); // GET 요청이므로 본문 없음

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    mainHandler.post(() -> {
                        showToast("좌석 연장 완료.");
                        initializeSeatData(); // 좌석 상태 새로고침 (남은 시간 등 업데이트)
                    });
                } else {
                    String errorResponse = convertInputStreamToString(connection.getErrorStream());
                    Log.e(TAG, "좌석 연장 실패: " + responseCode + ", " + errorResponse);
                    mainHandler.post(() -> showToast("좌석 연장 실패: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 연장 중 오류", e);
                mainHandler.post(() -> showToast("좌석 연장 중 오류가 발생했습니다."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                mainHandler.post(this::dismissLoadingDialog);
            }
        });
    }

     private void showSeatAssignmentDialog(int seatNumber) {
        new AlertDialog.Builder(this)
            .setTitle("좌석 선택")
            .setMessage(seatNumber + "번 좌석을 예약하시겠습니까?")
            .setPositiveButton("예약", (dialog, which) -> {
                assignSeat(seatNumber);
                dialog.dismiss();
            })
            .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void assignSeat(int seatNumber) {
        showLoadingDialog();
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                final String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
                if (jwtToken.isEmpty()) {
                    Log.e(TAG, "JWT 토큰이 없습니다.");
                    mainHandler.post(() -> showToast("로그인이 필요합니다."));
                    return;
                }

                final URL url = new URL(BASE_URL + "/reservation/reserve");
                Log.d(TAG, "좌석 예약 API 호출: " + url.toString());
                
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("seatId", String.valueOf(seatNumber));
                String requestBody = jsonBody.toString();
                Log.d(TAG, "예약 요청 본문: " + requestBody);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "예약 API 응답 코드: " + responseCode);
                
                if (responseCode == 200) {
                    String response = convertInputStreamToString(connection.getInputStream());
                    Log.d(TAG, "예약 API 응답: " + response);
                    
                    // 예약 성공 시 즉시 UI 업데이트
                    String seatId = String.valueOf(seatNumber);
                    reservedSeats.add(seatId);
                    seatStatusMap.put(seatId, new SeatStatus(120, 2)); // 기본값 설정
                    
                    mainHandler.post(() -> {
                        showToast(seatNumber + "번 좌석이 예약되었습니다.");
                        // UI 즉시 업데이트
                        updateSeatLayout();
                        // 서버에서 최신 데이터 가져오기
                        initializeSeatData();
                    });
                } else {
                    String errorResponse = convertInputStreamToString(connection.getErrorStream());
                    Log.e(TAG, "좌석 예약 실패: " + responseCode + ", 응답: " + errorResponse);
                    mainHandler.post(() -> showToast("좌석 예약 실패: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "좌석 예약 중 오류", e);
                mainHandler.post(() -> showToast("좌석 예약 중 오류가 발생했습니다."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                mainHandler.post(this::dismissLoadingDialog);
            }
        });
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
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
        mainHandler.post(() -> Toast.makeText(RealSeatReservation.this, message, Toast.LENGTH_SHORT).show());
    }

    private void showLoadingDialog() {
        mainHandler.post(() -> {
            if (loadingDialog == null || !loadingDialog.isShowing()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setView(R.layout.loading_dialog); // 로딩 다이얼로그 레이아웃이 따로 있다고 가정
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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    // SeatStatus 내부 클래스 정의
    private static class SeatStatus {
        long remainingMinutes;
        int numOfExtensions;

        SeatStatus(long remainingMinutes, int numOfExtensions) {
            this.remainingMinutes = remainingMinutes;
            this.numOfExtensions = numOfExtensions;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}