package com.example.test_03_04;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class SimpleLoginActivity extends AppCompatActivity {
    private static final String TAG = "SimpleLoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        EditText editTextId = findViewById(R.id.editTextId);
        Button loginButton = findViewById(R.id.loginButton);

        // 로그인 버튼 클릭 시 바로 다음 화면으로 이동
        loginButton.setOnClickListener(v -> {
            String email = editTextId.getText().toString(); // 입력된 ID를 문자열로 저장
            Log.d(TAG, "입력된 ID: " + email); // 로그로 ID 출력
            
            Intent intent = new Intent(SimpleLoginActivity.this, SeatReservationActivity.class);
            intent.putExtra("USER_ID", email); // 다음 화면으로 ID 전달
            startActivity(intent);
            finish();
        });
    }
}