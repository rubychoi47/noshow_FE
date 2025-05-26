package com.example.test_03_04;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import okhttp3.*;

public class RealSeatReservation extends AppCompatActivity {

    private final Set<String> reservedSeats = new HashSet<>();
    private String mySeatId = null;
    private static final String AUTH_PREF_NAME = "auth";
    private static final String JWT_TOKEN_KEY = "jwt_token";
    private final Map<String, Button> seatButtons = new HashMap<>();
    private final OkHttpClient client = new OkHttpClient();

    interface JsonCallback {
        void onSuccess(JSONObject json);
        void onFailure(Exception e);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat);

        ImageButton BackButton = findViewById(R.id.buttonback);

        if (BackButton != null) {
            BackButton.setOnClickListener(v -> {
                startActivity(new Intent(RealSeatReservation.this, SeatReservationActivity.class));
                finish();
            });
        }

        setupSeatUI();
        fetchReservedSeats();
        fetchMySeatStatus();
    }

    private void performJsonGetRequest(String url, JsonCallback callback) {
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> callback.onFailure(e));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) throw new IOException("HTTP error: " + response.code());
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    runOnUiThread(() -> callback.onSuccess(json));
                } catch (Exception e) {
                    runOnUiThread(() -> callback.onFailure(e));
                }
            }
        });
    }

    private void fetchReservedSeats() {
        performJsonGetRequest("https://www.noshow2025.shop/api/seat/reserved", new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                try {
                    JSONArray array = json.getJSONArray("reservedSeats");
                    reservedSeats.clear();
                    for (int i = 0; i < array.length(); i++) {
                        reservedSeats.add(array.getString(i));
                    }
                    updateSeatUI();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchMySeatStatus() {
        performJsonGetRequest("https://www.noshow2025.shop/api/seat/status", new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                mySeatId = json.optString("seatId", null);
            }
            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupSeatUI() {
        LinearLayout layout = findViewById(R.id.seatContainer);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);

        for (int i = 1; i <= 60; i++) {
            if ((i - 1) % 10 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                layout.addView(row);
            }
            String seatId = String.valueOf(i);
            Button button = new Button(this);
            button.setText(seatId);
            button.setTextColor(Color.BLACK);
            button.setTextSize(12f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(100, 120);
            params.setMargins(8, 8, 8, 8);
            button.setLayoutParams(params);
            button.setOnClickListener(v -> handleSeatClick(seatId));
            row.addView(button);
            seatButtons.put(seatId, button);
        }
    }

    private void updateSeatUI() {
        for (Map.Entry<String, Button> entry : seatButtons.entrySet()) {
            String seatId = entry.getKey();
            Button button = entry.getValue();
            boolean isReserved = reservedSeats.contains(seatId);
            if (isReserved) {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.seat_disavailable));
            } else {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.seat_available));
            }
        }
    }

    private void handleSeatClick(String seatId) {
        boolean isReserved = reservedSeats.contains(seatId);
        if (mySeatId == null) {
            if (isReserved) showOtherUserStatus(seatId, false);
            else showReservationDialog(seatId);
        } else {
            if (seatId.equals(mySeatId)) showMySeatDialog(seatId);
            else {
                showOtherUserStatus(seatId, true);
            }
        }
    }


    private void showReservationDialog(String seatId) {
        new AlertDialog.Builder(this)
                .setTitle("좌석 예약")
                .setMessage(seatId + "번 좌석을 예약하시겠습니까?")
                .setPositiveButton("예약", (dialog, which) -> doReserveRequest(seatId))
                .setNegativeButton("취소", null)
                .show();
    }

    private void showMySeatDialog(String seatId) {
        new AlertDialog.Builder(this)
                .setTitle("내 좌석 관리")
                .setMessage(seatId + "번 좌석\n무엇을 하시겠습니까?")
                .setPositiveButton("연장", (dialog, which) -> sendExtendRequest())
                .setNegativeButton("반납", (dialog, which) -> sendExitRequest())
                .setNeutralButton("취소", null)
                .show();
    }

    private void showOtherUserStatus(String seatId, boolean showWarning) {
        performJsonGetRequest("https://www.noshow2025.shop/api/seat/otherUserStatus?seatId=" + seatId, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                long minutes = json.optLong("remainingMinutes", 0);
                int extensions = json.optInt("numOfExtensions", 0);
                String message = seatId + "번 좌석\n남은 시간: " + minutes + "분\n연장 가능 횟수: " + extensions + "회";

                if(showWarning) {
                    message += "\n\n이미 좌석을 예약 중입니다. 다른 좌석을 선택할 수 없습니다." ;
                }
                new AlertDialog.Builder(RealSeatReservation.this)
                        .setTitle("좌석 정보")
                        .setMessage(message)
                        .setPositiveButton("확인", null)
                        .show();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(RealSeatReservation.this, "정보 조회 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doReserveRequest(String seatId) {
        String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("seatId", seatId);
            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url("https://www.noshow2025.shop/api/reservation/reserve")
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(RealSeatReservation.this, "예약 실패", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            mySeatId = seatId;
                            Toast.makeText(RealSeatReservation.this, "예약 성공", Toast.LENGTH_SHORT).show();
                            fetchReservedSeats();
                        } else {
                            Toast.makeText(RealSeatReservation.this, "예약 실패", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendExtendRequest() {
        sendSimplePost("https://www.noshow2025.shop/api/reservation/extend");
    }

    private void sendExitRequest() {
        sendSimplePost("https://www.noshow2025.shop/api/reservation/exit", () -> mySeatId = null);
    }

    private void sendSimplePost(String url) {
        sendSimplePost(url, null);
    }

    private void sendSimplePost(String url, Runnable onSuccess) {
        String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(RequestBody.create(new byte[0]))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        if (onSuccess != null) onSuccess.run();
                        fetchReservedSeats();
                    });
                }
            }
        });
    }
}
