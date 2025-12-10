package devyana.kekita.printbridge.Activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import devyana.kekita.printbridge.Helper.DatabaseHelper;
import devyana.kekita.printbridge.R;

public class WizardActivity extends AppCompatActivity {

    private TextInputEditText inputKode;
    private MaterialButton btnAktivasi;
    private ProgressBar progressBar;
    private DatabaseHelper dbHelper;
    private static final String API_VERIFY_URL = "https://devyana.my.id/verify.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_wizard);

        dbHelper = new DatabaseHelper(this);

        // Cek apakah sudah ada pengaturan. Jika ada, langsung ke MainActivity.
        // Kita cek salah satu kunci penting, misal "client".
        if (dbHelper.getSetting("client") != null) {
            startMainActivity();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#F4F4F4"));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        inputKode = findViewById(R.id.input_kode);
        btnAktivasi = findViewById(R.id.btn_aktivasi);
        progressBar = findViewById(R.id.progress_bar);

        btnAktivasi.setOnClickListener(v -> {
            inputKode.clearFocus();

            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

            String kodeAkses = inputKode.getText().toString().trim();
            if (kodeAkses.isEmpty()) {
                Toast.makeText(this, "Kode akses tidak boleh kosong", Toast.LENGTH_SHORT).show();
            } else {
                aktivasi(kodeAkses);
            }
        });
    }

    private void aktivasi(String kodeAkses) {
        setLoading(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            // Proses di background thread
            try {
                // 1. Panggil API
                URL url = new URL(API_VERIFY_URL + "?kode=" + kodeAkses);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    // Baca response JSON
                    InputStream in = conn.getInputStream();
                    Scanner s = new Scanner(in).useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";
                    s.close();

                    JSONObject json = new JSONObject(response);
                    JSONObject settings = json.getJSONObject("settings");

                    // 2. Download logo dan simpan path-nya
                    String logoUrl = settings.getString("logo");
                    String logoPath = downloadAndSaveImage("logo.png", logoUrl);

                    String logoPrintUrl = settings.getString("logo_print");
                    String logoPrintPath = downloadAndSaveImage("logo_print.png", logoPrintUrl);

                    // 3. Simpan semua pengaturan ke SQLite
                    dbHelper.saveSetting("client", json.getString("client"));
                    dbHelper.saveSetting("url", settings.getString("url"));
                    dbHelper.saveSetting("logo", logoPath);
                    dbHelper.saveSetting("logo_print", logoPrintPath);
                    dbHelper.saveSetting("header_text", settings.getString("header_text"));
                    dbHelper.saveSetting("footer_text", settings.getString("footer_text"));

                    // Pindah ke MainActivity setelah sukses
                    handler.post(this::startMainActivity);
                } else {
                    // Gagal verifikasi
                    handler.post(() -> {
                        setLoading(false);
                        Toast.makeText(WizardActivity.this, "Kode akses tidak valid!", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e("WizardActivity", "Aktivasi gagal", e);
                handler.post(() -> {
                    setLoading(false);
                    Toast.makeText(WizardActivity.this, "Terjadi error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String downloadAndSaveImage(String fileName, String downloadUrl) throws Exception {
        File internalStorageDir = getApplicationContext().getFilesDir();
        File imageFile = new File(internalStorageDir, fileName);

        InputStream inputStream = new URL(downloadUrl).openStream();
        OutputStream outputStream = new FileOutputStream(imageFile);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.close();
        inputStream.close();

        return imageFile.getAbsolutePath();
    }

    private void startMainActivity() {
        Intent intent = new Intent(WizardActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnAktivasi.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnAktivasi.setEnabled(true);
        }
    }
}