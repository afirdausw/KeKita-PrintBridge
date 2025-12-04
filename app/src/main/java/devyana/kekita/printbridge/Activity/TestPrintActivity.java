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
    }

    public void onBack(View view) {
        finish();
    }

    private void doTestPrint() {
        try {
            // JSON sample dari kamu
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

            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.getJSONObject("data");
            JSONArray items = root.getJSONArray("items");

            // ambil lebar kertas dari prefs
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String widthStr = prefs.getString(KEY_PAPER_WIDTH, "58");
            int paperWidth = widthStr.equals("80") ? 47 : 32;

            EscPosFormatter f = new EscPosFormatter(paperWidth);
            StringBuilder sb = new StringBuilder();

            // Header toko
            sb.append(f.center("KeKita FnB"));
            sb.append(f.center("Gg. Gladak Serang 1 No.56"));
            sb.append(f.center("KOTA PROBOLINGGO"));
            sb.append(f.center("Open : Daily 09am-10pm"));
            sb.append(f.separator());

            // Info transaksi
            sb.append(f.left("Invoice  : #" + data.optString("invoice", "")));
            String jam = data.optString("jam_transaksi", "");
            if (jam != null && jam.length() >= 5) jam = jam.substring(0, 5);
            sb.append(f.left("Time     : " + data.optString("tanggal_transaksi", "") + " " + jam));
            sb.append(f.left("Cashier  : " + data.optString("nama_lengkap", "")));
            sb.append(f.left("Table    : " + data.optString("meja", "")));
            sb.append(f.separator());

            // Items
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                String name = it.optString("nama_produk", "");
                String varian = it.optString("nama_varian", "");
                String note = it.optString("catatan_item", "");
                int qty = parseIntSafe(it.optString("jumlah_produk","0"));
                int subtotal = parseIntSafe(it.optString("subtotal","0"));

                if (varian != null && !"null".equals(varian) && !varian.isEmpty()) {
                    name += " - " + varian;
                }

                sb.append(f.formatItem(name, qty, subtotal)).append("\n");

                if (note != null && !"null".equals(note) && !note.isEmpty()) {
                    sb.append(f.subLine(note));
                }
            }

            // Total
            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth-10) + "s %10s\n", "Subtotal", f.formatNumber(data.optString("total_pesanan","0"))));
            sb.append(String.format("%-" + (paperWidth-10) + "s %10s\n", "Service", f.formatNumber(data.optString("total_service","0"))));
            sb.append(String.format("%-" + (paperWidth-10) + "s %10s\n", "Tax", f.formatNumber(data.optString("total_ppn","0"))));
            sb.append(String.format("%-" + (paperWidth-10) + "s %10s\n", "Total", f.formatNumber(data.optString("total","0"))));
            sb.append(String.format("%-" + (paperWidth-10) + "s %10s\n", "Rounding", f.formatNumber(data.optString("nilai_pembulatan","0"))));
            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth-10) + "s %10s\n", "TOTAL PAID", f.formatNumber(data.optString("total_harus_dibayar","0"))));
            sb.append(f.separator());
            sb.append(f.center("Thank You"));
            sb.append(f.center("# Instagram : @kekita.agency"));
            sb.append(f.center("# Wifi Password : trustME!"));

            // debug ke logcat
            Log.d(TAG, "Print buffer:\n" + sb);

            // TODO: kirim ke PrinterService -> Bluetooth
            Toast.makeText(this, "Print data siap", Toast.LENGTH_SHORT).show();

            // kirim ke PrinterService
            Intent i = new Intent(this, PrinterService.class);
            i.setAction(PrinterService.ACTION_PRINT);
            i.putExtra("text", sb.toString());
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
