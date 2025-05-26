package com.example.test_03_04;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MySeatActivity extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(MySeatActivity.class);
    private LinearLayout top5Container;
    private Button btnOk, btnReturn, btnProlongation;
    private static final String AUTH_PREF_NAME = "auth";
    private static final String JWT_TOKEN_KEY = "jwt_token";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_myseat_notice);

        TextView remainingTime = findViewById(R.id.remainingTime);
        TextView extension = findViewById(R.id.extendCount);

        Button okButton = findViewById(R.id.btnOk);
        Button exitButton = findViewById(R.id.btnReturn);
        Button qrShowButton = findViewById(R.id.btnProlongation);
        fetchMySeatInfo(remainingTime, extension);
        if (okButton != null) {
            okButton.setOnClickListener(v -> {
                startActivity(new Intent(MySeatActivity.this, SeatReservationActivity.class));
                finish();
            });
        }

        if (exitButton != null) {
            exitButton.setOnClickListener(v -> sendReturnRequest());
        }

        if (qrShowButton != null) {
            qrShowButton.setOnClickListener(v -> sendExtendRequest());
        }
    }

    private void fetchMySeatInfo(TextView remainingTimeView, TextView extensionView) {
        String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!jwtToken.startsWith("Bearer ")) {
            jwtToken = "Bearer " + jwtToken;
        }

        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/seat/status")
                .addHeader("Authorization", jwtToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MySeatActivity.this, "서버 요청 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(MySeatActivity.this, "좌석 정보를 불러올 수 없습니다", Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    JSONObject obj = new JSONObject(response.body().string());
                    JSONObject body = obj.getJSONObject("body");

                    String seatId = body.getString("seatId");
                    long remainingMinutes = body.getLong("remainingMinutes");
                    int numOfExtensions = body.getInt("numOfExtensions");
                    Log.d("MySeatActivity", "남은 시간: " + remainingMinutes + "분, 연장 가능 횟수: " + numOfExtensions + "회");
                    runOnUiThread(() -> {
                        String timeFormatted = formatTime(remainingMinutes);
                        remainingTimeView.setText("남은 시간: " + timeFormatted);
                        extensionView.setText("연장 가능한 횟수: " + numOfExtensions + "/3회");
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private void sendReturnRequest() {
        String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
        if (jwtToken.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show());
            return;
        }

        if (!jwtToken.startsWith("Bearer ")) {
            jwtToken = "Bearer " + jwtToken;
        }
        RequestBody body = RequestBody.create("", MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/exit")
                .addHeader("Authorization", jwtToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MySeatActivity.this, "반납 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(MySeatActivity.this, "반납 완료", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendExtendRequest() {
        String jwtToken = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE).getString(JWT_TOKEN_KEY, "");
        if (jwtToken.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show());
            return;
        }

        if (!jwtToken.startsWith("Bearer ")) {
            jwtToken = "Bearer " + jwtToken;
        }
        RequestBody body = RequestBody.create("", MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url("https://www.noshow2025.shop/api/reservation/extend")
                .addHeader("Authorization", jwtToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MySeatActivity.this, "연장 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(MySeatActivity.this, "연장 완료", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private String formatTime(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}

