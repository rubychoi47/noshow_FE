package com.example.test_03_04;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RealSeatReservation extends AppCompatActivity {

    private final Set<String> reservedSeats = new HashSet<>();
    private String mySeatId = null;
    private final Map<String, Button> seatButtons = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat);

        setupSeatUI();
        fetchReservedSeats();
        fetchMySeatStatus();
    }

    private void fetchReservedSeats() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seat/reserved")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                JSONArray array = null;
                try {
                    array = new JSONArray(body);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                reservedSeats.clear();
                for (int i = 0; i < array.length(); i++) {
                    try {
                        reservedSeats.add(array.getString(i));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                runOnUiThread(() -> updateSeatUI());
            }
        });
    }

    private void fetchMySeatStatus() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seat/status")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                JSONObject json = null;
                try {
                    json = new JSONObject(body);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                try {
                    mySeatId = json.isNull("seatId") ? null : json.getString("seatId");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void setupSeatUI() {
        LinearLayout layout = findViewById(R.id.seatContainer);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);

        for (int i = 1; i <= 180; i++) {
            if ((i - 1) % 20 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                layout.addView(row);
            }

            String seatId = String.valueOf(i);
            Button button = new Button(this);
            button.setText(seatId);
            button.setTextColor(Color.WHITE);
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
            int id = Integer.parseInt(seatId);
            boolean isReserved = reservedSeats.contains(seatId);

            if (isReserved) {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.seat_disavailable));
            } else if ((id >= 1 && id <= 25) || (id >= 99 && id <= 106) || (id >= 148 && id <= 157)) {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.seat_available_window));
            } else {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.seat_available));
            }
        }
    }

    private void handleSeatClick(String seatId) {
        boolean isReserved = reservedSeats.contains(seatId);
        if (mySeatId == null) {
            if (isReserved) {
                showOtherUserStatus(seatId);
            } else {
                showReservationDialog(seatId);
            }
        } else {
            if (seatId.equals(mySeatId)) {
                showMySeatDialog(seatId);
            } else if (isReserved) {
                showOtherUserStatus(seatId);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("예약 불가")
                        .setMessage("이미 좌석을 예약 중입니다. 다른 좌석을 선택할 수 없습니다.")
                        .setPositiveButton("확인", null)
                        .show();
            }
        }
    }

    private void showReservationDialog(String seatId) {
        new AlertDialog.Builder(this)
                .setTitle("좌석 예약")
                .setMessage(seatId + "번 좌석을 예약하시겠습니까?")
                .setPositiveButton("예약", (dialog, which) -> {
                    try {
                        sendReservationRequest(seatId);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
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

    private void showOtherUserStatus(String seatId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seat/otherUserStatus?seatId=" + seatId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                JSONObject json = null;
                try {
                    json = new JSONObject(body);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                long minutes = json.optLong("remainingMinutes", 0);
                int extensions = json.optInt("numOfExtensions", 0);
                String message = seatId + "번 좌석\n남은 시간: " + minutes + "분\n연장 가능 횟수: " + extensions + "회";

                runOnUiThread(() -> new AlertDialog.Builder(RealSeatReservation.this)
                        .setTitle("좌석 정보")
                        .setMessage(message)
                        .setPositiveButton("확인", null)
                        .show());
            }
        });
    }

    private void sendReservationRequest(String seatId) throws JSONException {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        json.put("seatId", seatId);

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(json.toString(), mediaType);

        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/reserve")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(RealSeatReservation.this, "예약 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(RealSeatReservation.this, "예약 성공", Toast.LENGTH_SHORT).show();
                        fetchReservedSeats();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(RealSeatReservation.this, "예약 실패", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void sendExtendRequest() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/extend")
                .post(RequestBody.create(new byte[0]))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) {}
        });
    }

    private void sendExitRequest() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/exit")
                .post(RequestBody.create(new byte[0]))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    mySeatId = null;
                    runOnUiThread(() -> fetchReservedSeats());
                }
            }
        });
    }

    static class SeatInfo {
        String seatId;
        long remainingMinutes;
        int numOfExtensions;

        SeatInfo(String seatId, long remainingMinutes, int numOfExtensions) {
            this.seatId = seatId;
            this.remainingMinutes = remainingMinutes;
            this.numOfExtensions = numOfExtensions;
        }
    }
}
