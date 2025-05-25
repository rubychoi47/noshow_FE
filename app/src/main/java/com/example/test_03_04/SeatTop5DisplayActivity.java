package com.example.test_03_04;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

public class SeatTop5DisplayActivity extends AppCompatActivity {

    private LinearLayout top5Container;
    private Button btnSortByTime, btnSortByExtension, btnSortByFavorite, textTimer;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.seat_reservation);
        top5Container = findViewById(R.id.top5Container);
        btnSortByTime = findViewById(R.id.btnSortByTime);
        btnSortByExtension = findViewById(R.id.btnSortByExtension);
        btnSortByFavorite = findViewById(R.id.btnSortByFavorite);
        textTimer = findViewById(R.id.textTimer);

        btnSortByTime.setOnClickListener(v -> fetchTop5ByRemainingTime());
        btnSortByExtension.setOnClickListener(v -> fetchTop5ByExtension());
        btnSortByFavorite.setOnClickListener(v -> fetchFavoriteSeats());
        textTimer.setOnClickListener(v -> showSeatStatusDialog());
    }
    private static final String TAG = "SeatTop5DisplayActivity";
    private void fetchTop5ByRemainingTime() {
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seats/remainingTime")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "zzz-----------------------------9");
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    Log.d(TAG, "zzz-----------------------------0");
                    JSONArray jsonArray = new JSONArray(response.body().string());
                    Log.d(TAG, "zzz-----------------------------1");
                    List<SeatRemainingTimeResponse> list = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        list.add(new SeatRemainingTimeResponse(
                                obj.getString("seatId"),
                                obj.getLong("remainingMinutes")
                        ));
                    }
                    //list.sort(Comparator.comparingLong(a -> a.remainingMinutes));
                    List<SeatRemainingTimeResponse> top5 = list.subList(0, Math.min(5, list.size()));
                    Log.d(TAG, "zzz-----------------------------2");
                    runOnUiThread(() -> updateTop5Text(top5));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

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
                    List<SeatRemainingNumOfExtensionResponse> list = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        list.add(new SeatRemainingNumOfExtensionResponse(
                                obj.getString("seatId"),
                                obj.getInt("numOfExtensions"),
                                obj.getLong("remainingMinutes")
                        ));
                    }
                    list.sort(Comparator.comparingInt((SeatRemainingNumOfExtensionResponse s) -> s.numOfExtensions)
                            .thenComparingLong(s -> s.remainingMinutes));
                    List<SeatRemainingNumOfExtensionResponse> top5 = list.subList(0, Math.min(5, list.size()));
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
                runOnUiThread(() -> Toast.makeText(SeatTop5DisplayActivity.this, "서버 요청 실패", Toast.LENGTH_SHORT).show());
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

    private void updateTop5Text(List<SeatRemainingTimeResponse> list) {
        top5Container.removeAllViews();
        for (SeatRemainingTimeResponse item : list) {
            TextView tv = new TextView(this);
            tv.setText("좌석 " + item.seatId + " - " + item.remainingMinutes + "분 남음");
            tv.setTextSize(16f);
            top5Container.addView(tv);
        }
    }

    private void updateTop5TextByExtension(List<SeatRemainingNumOfExtensionResponse> list) {
        top5Container.removeAllViews();
        for (SeatRemainingNumOfExtensionResponse item : list) {
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

    private void showSeatStatusDialog() {
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seat/status")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SeatTop5DisplayActivity.this, "서버 요청 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONObject obj = new JSONObject(response.body().string());
                    String seatId = obj.getString("seatId");
                    long remainingMinutes = obj.getLong("remainingMinutes");
                    int numOfExtensions = obj.getInt("numOfExtensions");
                    runOnUiThread(() -> showSeatDialog(seatId, remainingMinutes, numOfExtensions));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
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

    private void sendReturnRequest() {
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/exit")
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SeatTop5DisplayActivity.this, "반납 요청 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> Toast.makeText(SeatTop5DisplayActivity.this, "좌석 반납 완료", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendExtendRequest() {
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/extend")
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SeatTop5DisplayActivity.this, "연장 요청 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> Toast.makeText(SeatTop5DisplayActivity.this, "좌석 연장 완료", Toast.LENGTH_SHORT).show());
            }
        });
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

