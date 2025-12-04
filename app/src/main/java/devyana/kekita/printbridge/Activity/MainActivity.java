package devyana.kekita.printbridge.Activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import devyana.kekita.printbridge.Helper.DatabaseHelper;
import devyana.kekita.printbridge.Helper.EscPosImageHelper;
import devyana.kekita.printbridge.Printer.PrinterService;
import devyana.kekita.printbridge.R;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = PrinterService.PREFS;
    private static final String KEY_PRINTER_ADDR = PrinterService.KEY_PRINTER_ADDR;
    private static final String KEY_PRINTER_NAME = PrinterService.KEY_PRINTER_NAME;
    private static final String KEY_PAPER_WIDTH = PrinterService.KEY_PAPER_WIDTH;

    private ImageView ivClientLogo;
    private TextView tvClientName, tvPrinter;
    private MaterialButton btnStart, btnStop, btnChoose, btnPaperWidth;

    // launcher untuk membuka activity pilih device
    private ActivityResultLauncher<Intent> pickerLauncher;
    private static final int REQ_ALL_PERMISSIONS = 2001;

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        if (dbHelper.getSetting("client") == null) {
            Intent wizardIntent = new Intent(MainActivity.this, WizardActivity.class);
            startActivity(wizardIntent);
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.setStatusBarColor(Color.WHITE);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ivClientLogo = findViewById(R.id.iv_client_logo);
        tvClientName = findViewById(R.id.tv_client_name);
        tvPrinter = findViewById(R.id.tv_printer);
        btnStart = findViewById(R.id.btn_start_service);
        btnStop = findViewById(R.id.btn_stop_service);
        btnChoose = findViewById(R.id.btn_choose_device);
        btnPaperWidth = findViewById(R.id.btn_paper_width);

        pickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> refreshPrinterInfo());

        btnChoose.setOnClickListener(v -> {
            if (hasBluetoothPermissions()) {
                Intent i = new Intent(this, BluetoothPickerActivity.class);
                pickerLauncher.launch(i);
            } else {
                requestBluetoothPermissions();
            }
        });

        btnStart.setOnClickListener(v -> startBridgeService());
        btnStop.setOnClickListener(v -> stopBridgeService());
        btnPaperWidth.setOnClickListener(v -> showPaperWidthDialog());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2002);
            }
        }

        loadAndDisplaySettings();

        requestAllPermissions();
        ensureBluetoothEnabled();
        refreshPrinterInfo();
    }

    private void loadAndDisplaySettings() {
        Map<String, String> settings = dbHelper.getAllSettings();

        String clientName = settings.get("client");
        if (clientName != null) {
            tvClientName.setText(clientName);
        } else {
            tvClientName.setText("Nama Klien Tidak Ditemukan");
        }

        // 3. Tampilkan logo
        String logoPath = settings.get("logo");
        if (logoPath != null && !logoPath.isEmpty()) {
            File logoFile = new File(logoPath);
            if (logoFile.exists()) {
                Bitmap logoBitmap = BitmapFactory.decodeFile(logoFile.getAbsolutePath());
                ivClientLogo.setImageBitmap(logoBitmap);
            } else {
                ivClientLogo.setImageResource(R.mipmap.ic_launcher);
            }
        } else {
            ivClientLogo.setImageResource(R.mipmap.ic_launcher);
        }
    }

    private void startBridgeService() {
        Intent svc = new Intent(this, PrinterService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        Toast.makeText(this, "Service starting...", Toast.LENGTH_SHORT).show();
    }

    private void stopBridgeService() {
        Intent svc = new Intent(this, PrinterService.class);
        stopService(svc);
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }

    private void refreshPrinterInfo() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String mac = prefs.getString(KEY_PRINTER_ADDR, null);
        String name = prefs.getString(KEY_PRINTER_NAME, null);
        if (mac != null) {
            tvPrinter.setText("Printer: " + (name != null ? name : "Unknown") + "\nMAC: " + mac);
        } else {
            tvPrinter.setText("No printer selected");
        }
    }

    private void showPaperWidthDialog() {
        String[] options = {"58mm", "80mm"};
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String current = prefs.getString(KEY_PAPER_WIDTH, "58");
        int checkedItem = current.equals("80") ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("Tentukan Lebar Kertas")
                .setSingleChoiceItems(options, checkedItem, null)
                .setPositiveButton("Simpan", (dialog, whichButton) -> {
                    int selected = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    String width = selected == 0 ? "58" : "80";
                    prefs.edit().putString(KEY_PAPER_WIDTH, width).apply();
                    Toast.makeText(this, "Paper width set to " + width + "mm", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void requestAllPermissions() {
        List<String> list = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (!list.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    list.toArray(new String[0]),
                    REQ_ALL_PERMISSIONS
            );
        }
    }

    private void ensureBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 3002);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_ALL_PERMISSIONS) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            }
            if (!ok) {
                Toast.makeText(this, "All permissions are required for proper function", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == 3001) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                Intent i = new Intent(this, BluetoothPickerActivity.class);
                pickerLauncher.launch(i);
            } else {
                Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    }, 3001);
        }
    }

    public void onTest(View view) {
        startActivity(new Intent(getApplicationContext(), TestPrintActivity.class));
    }
}
