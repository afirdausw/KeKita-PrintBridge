package devyana.kekita.printbridge.Activity;

import static devyana.kekita.printbridge.Printer.PrinterService.KEY_PAPER_WIDTH;
import static devyana.kekita.printbridge.Printer.PrinterService.PREFS;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import devyana.kekita.printbridge.Printer.EscPosFormatter;
import devyana.kekita.printbridge.Printer.PrinterService;
import devyana.kekita.printbridge.R;

public class TestPrintActivity extends AppCompatActivity {

    private static final String TAG = "TestPrintActivity";
    private MaterialButton btnPrint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_test_print);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#F4F4F4"));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        btnPrint = findViewById(R.id.btn_print);
        btnPrint.setOnClickListener(v -> doTestPrint());

        updatePreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePreview();
    }

    public void onBack(View view) {
        finish();
    }

    private void updatePreview() {
        android.widget.TextView tvPreview = findViewById(R.id.tv_preview);
        android.widget.TextView tvTemplateName = findViewById(R.id.tv_template_name);

        devyana.kekita.printbridge.Helper.DatabaseHelper dbHelper = new devyana.kekita.printbridge.Helper.DatabaseHelper(this);
        
        String savedLang = dbHelper.getSetting("language");
        String langStr = "indonesia".equalsIgnoreCase(savedLang) ? "Indonesia" : "English";
        
        String savedTemp = dbHelper.getSetting("template");
        String tempStr = "Template #1";
        if ("template_2".equals(savedTemp)) tempStr = "Template #2";
        else if ("template_3".equals(savedTemp)) tempStr = "Template #3";

        android.content.SharedPreferences prefs = getSharedPreferences(devyana.kekita.printbridge.Printer.PrinterService.PREFS, MODE_PRIVATE);
        String widthStr = prefs.getString(devyana.kekita.printbridge.Printer.PrinterService.KEY_PAPER_WIDTH, "58");
        String paperStr = widthStr.equals("80") ? "80mm" : "58mm";
        
        if (tvTemplateName != null) {
            if ("template_3".equals(savedTemp)) {
                tvTemplateName.setText("Campuran, " + tempStr + ", " + paperStr);
            } else {
                tvTemplateName.setText(langStr + ", " + tempStr + ", " + paperStr);
            }
        }

        if (tvPreview != null) {
            tvPreview.setText(generateReceiptString());
        }
    }

    private String generateReceiptString() {
        try {
            String jsonStr = "{ \"menu\":\"Kasir\",\"title\":\"Print Struk #250823001\","
                    + "\"data\":{"
                    + "\"id_transaksi\":\"5\",\"tanggal_transaksi\":\"2025-06-09\",\"jam_transaksi\":\"12:30:27\","
                    + "\"customer\":\"Dine In\",\"meja\":\"02\",\"invoice\":\"250823001\",\"total_pesanan\":\"143000\","
                    + "\"total_diskon_potongan\":\"0\",\"total_service\":\"7150\",\"total_ppn\":\"14300\",\"total\":\"164450\","
                    + "\"nilai_pembulatan\":\"550\",\"total_harus_dibayar\":\"165000\",\"bayar\":\"165000\","
                    + "\"status_transaksi\":\"Selesai\",\"tipe_transaksi\":\"Normal\",\"pengguna_id_kasir\":\"4\","
                    + "\"created_at\":\"2025-08-23 12:28:06\",\"updated_at\":\"2025-08-23 12:34:04\","
                    + "\"nama_lengkap\":\"Kasir Pagi\",\"item\":\"4\"},"
                    + "\"items\":["
                    + "{\"id_detail_transaksi\":\"16\",\"nama_produk\":\"Special Juice\",\"nama_varian\":\"Carrot\",\"jumlah_produk\":\"1\",\"subtotal\":\"30000\"},"
                    + "{\"id_detail_transaksi\":\"17\",\"nama_produk\":\"Cappucino\",\"catatan_item\":\"Less sugar\",\"jumlah_produk\":\"1\",\"subtotal\":\"33000\"},"
                    + "{\"id_detail_transaksi\":\"18\",\"nama_produk\":\"Javanese Fried Noodle\",\"jumlah_produk\":\"1\",\"subtotal\":\"30000\"},"
                    + "{\"id_detail_transaksi\":\"19\",\"nama_produk\":\"Chicken Cordon Bleu\",\"jumlah_produk\":\"1\",\"subtotal\":\"50000\"}"
                    + "]}";

            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String widthStr = prefs.getString(KEY_PAPER_WIDTH, "58");
            int paperWidth = widthStr.equals("80") ? 45 : 31;

            devyana.kekita.printbridge.Helper.DatabaseHelper dbHelper = new devyana.kekita.printbridge.Helper.DatabaseHelper(this);
            return devyana.kekita.printbridge.Helper.ReceiptBuilder.buildReceiptString(jsonStr, paperWidth, dbHelper);
        } catch (Exception e) {
            Log.e(TAG, "Error generating receipt string", e);
            return "Error generating preview: " + e.getMessage();
        }
    }

    private void doTestPrint() {
        try {
            String receiptStr = generateReceiptString();
            Log.d(TAG, "Print buffer:\n" + receiptStr);

            Toast.makeText(this, "Print data siap", Toast.LENGTH_SHORT).show();

            Intent i = new Intent(this, PrinterService.class);
            i.setAction(PrinterService.ACTION_PRINT);
            i.putExtra("text", receiptStr);
            startService(i);
        } catch (Exception e) {
            Log.e(TAG, "Error test print", e);
            Toast.makeText(this, "Error test print: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int parseIntSafe(String val) {
        try {
            return Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
