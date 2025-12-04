package devyana.kekita.printbridge.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Set;

import devyana.kekita.printbridge.Printer.PrinterService;
import devyana.kekita.printbridge.R;

public class BluetoothPickerActivity extends AppCompatActivity {
    private ListView lv;
    private ArrayAdapter<String> adapter;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_bluetooth_picker);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#F4F4F4"));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        lv = findViewById(R.id.list_paired);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lv.setAdapter(adapter);

        loadPaired();

        lv.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice d = devices.get(position);
            saveSelected(d);
            Toast.makeText(this, "Selected: " + d.getName(), Toast.LENGTH_SHORT).show();
            // finish and return
            setResult(RESULT_OK, new Intent());
            finish();
        });
    }

    public void onBack(View view) {
        finish();
    }

    private void loadPaired() {
        adapter.clear();
        devices.clear();
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth not available or disabled", Toast.LENGTH_LONG).show();
            return;
        }
        Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
        if (paired != null) {
            for (BluetoothDevice d : paired) {
                devices.add(d);
                adapter.add(d.getName() + "\n" + d.getAddress());
            }
        }
    }

    private void saveSelected(BluetoothDevice d) {
        SharedPreferences prefs = getSharedPreferences(PrinterService.PREFS, MODE_PRIVATE);
        prefs.edit()
                .putString(PrinterService.KEY_PRINTER_ADDR, d.getAddress())
                .putString(PrinterService.KEY_PRINTER_NAME, d.getName())
                .apply();
    }
}
