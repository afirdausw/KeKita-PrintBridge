package devyana.kekita.printbridge.Activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

import devyana.kekita.printbridge.Helper.DatabaseHelper;
import devyana.kekita.printbridge.R;
public class SettingsActivity extends AppCompatActivity {

    private devyana.kekita.printbridge.Helper.DatabaseHelper dbHelper;
    private android.widget.AutoCompleteTextView autocompleteLanguage;
    private android.widget.AutoCompleteTextView autocompleteTemplate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_settings);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#F4F4F4"));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        dbHelper = new devyana.kekita.printbridge.Helper.DatabaseHelper(this);
        autocompleteLanguage = findViewById(R.id.autocomplete_language);
        autocompleteTemplate = findViewById(R.id.autocomplete_template);

        setupDropdowns();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePreview();
    }

    private void setupDropdowns() {
        // Setup Language Dropdown
        String[] languages = {"English", "Indonesia"};
        android.widget.ArrayAdapter<String> langAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, languages);
        autocompleteLanguage.setAdapter(langAdapter);

        String savedLang = dbHelper.getSetting("language");
        if ("indonesia".equals(savedLang)) {
            autocompleteLanguage.setText(languages[1], false);
        } else {
            autocompleteLanguage.setText(languages[0], false); // Default English
        }

        autocompleteLanguage.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    dbHelper.saveSetting("language", "english");
                } else {
                    dbHelper.saveSetting("language", "indonesia");
                }
                updatePreview();
            }
        });

        // Setup Template Dropdown
        String[] templates = {"Template #1", "Template #2", "Template #3"};
        android.widget.ArrayAdapter<String> tempAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, templates);
        autocompleteTemplate.setAdapter(tempAdapter);

        String savedTemp = dbHelper.getSetting("template");
        View layoutLanguage = findViewById(R.id.layout_language);

        if ("template_3".equals(savedTemp)) {
            autocompleteTemplate.setText(templates[2], false);
            if (layoutLanguage != null) layoutLanguage.setVisibility(View.GONE);
        } else if ("template_2".equals(savedTemp)) {
            autocompleteTemplate.setText(templates[1], false);
            if (layoutLanguage != null) layoutLanguage.setVisibility(View.VISIBLE);
        } else {
            autocompleteTemplate.setText(templates[0], false); // Default Template #1
            if (layoutLanguage != null) layoutLanguage.setVisibility(View.VISIBLE);
        }

        autocompleteTemplate.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    dbHelper.saveSetting("template", "template_1");
                    if (layoutLanguage != null) layoutLanguage.setVisibility(View.VISIBLE);
                } else if (position == 1) {
                    dbHelper.saveSetting("template", "template_2");
                    if (layoutLanguage != null) layoutLanguage.setVisibility(View.VISIBLE);
                } else {
                    dbHelper.saveSetting("template", "template_3");
                    if (layoutLanguage != null) layoutLanguage.setVisibility(View.GONE);
                }
                updatePreview();
            }
        });

        // Setup Paper Width Dropdown
        android.widget.AutoCompleteTextView autocompletePaperWidth = findViewById(R.id.autocomplete_paper_width);
        String[] paperWidths = {"58mm", "80mm"};
        android.widget.ArrayAdapter<String> paperAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, paperWidths);
        autocompletePaperWidth.setAdapter(paperAdapter);

        android.content.SharedPreferences prefs = getSharedPreferences(devyana.kekita.printbridge.Printer.PrinterService.PREFS, MODE_PRIVATE);
        String savedPaperWidth = prefs.getString(devyana.kekita.printbridge.Printer.PrinterService.KEY_PAPER_WIDTH, "58");
        if ("80".equals(savedPaperWidth)) {
            autocompletePaperWidth.setText(paperWidths[1], false);
        } else {
            autocompletePaperWidth.setText(paperWidths[0], false);
        }

        autocompletePaperWidth.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String width = position == 0 ? "58" : "80";
                prefs.edit().putString(devyana.kekita.printbridge.Printer.PrinterService.KEY_PAPER_WIDTH, width).apply();
                updatePreview();
            }
        });

        updatePreview();
    }

    private void updatePreview() {
        android.widget.TextView tvPreview = findViewById(R.id.tv_preview);
        if (tvPreview == null) return;

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
                    + "{\"id_detail_transaksi\":\"18\",\"nama_produk\":\"Javanese Fried Noodle\",\"jumlah_produk\":\"1\",\"subtotal\":\"30000\"}"
                    + "]}";

            org.json.JSONObject root = new org.json.JSONObject(jsonStr);
            org.json.JSONObject data = root.getJSONObject("data");
            org.json.JSONArray items = root.getJSONArray("items");

            android.content.SharedPreferences prefs = getSharedPreferences(devyana.kekita.printbridge.Printer.PrinterService.PREFS, MODE_PRIVATE);
            String widthStr = prefs.getString(devyana.kekita.printbridge.Printer.PrinterService.KEY_PAPER_WIDTH, "58");
            int paperWidth = widthStr.equals("80") ? 45 : 31;

            String receiptStr = devyana.kekita.printbridge.Helper.ReceiptBuilder.buildReceiptString(jsonStr, paperWidth, dbHelper);
            tvPreview.setText(receiptStr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseIntSafe(String val) {
        try {
            return Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onShowConfig(View view) {
        try {
            java.util.Map<String, String> settings = dbHelper.getAllSettings();
            
            // Urutan yang diminta
            String[] keys = {"client", "url", "language", "template", "logo", "logo_print", "header_text", "footer_text"};
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                String value = settings.containsKey(key) ? settings.get(key) : "";
                
                // Escape quotes for valid JSON look, but keep / as is
                if (value != null) {
                    value = value.replace("\"", "\\\"");
                }
                
                sb.append("    \"").append(key).append("\": \"").append(value).append("\"");
                if (i < keys.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            // Tambahkan key lain yang mungkin ada (misal: template, paper_width) jika dibutuhkan, 
            // tapi user minta urutan spesifik, jadi kita tampilkan yang sisa di bawahnya.
            boolean hasOtherKeys = false;
            for (String k : settings.keySet()) {
                boolean found = false;
                for (String reqKey : keys) {
                    if (reqKey.equals(k)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (!hasOtherKeys) {
                        sb.deleteCharAt(sb.length() - 1); // remove last newline
                        sb.append(",\n");
                        hasOtherKeys = true;
                    }
                    String val = settings.get(k);
                    if (val != null) val = val.replace("\"", "\\\"");
                    sb.append("    \"").append(k).append("\": \"").append(val).append("\",\n");
                }
            }
            if (hasOtherKeys) {
                sb.delete(sb.length() - 2, sb.length()); // remove last comma and newline
                sb.append("\n");
            }
            sb.append("}");

            String finalJsonStr = sb.toString();

            // Custom View untuk AlertDialog (ScrollView + TextView Monospace)
            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            scrollView.setPadding(40, 20, 40, 20);
            
            android.widget.TextView tvJson = new android.widget.TextView(this);
            tvJson.setText(finalJsonStr);
            tvJson.setTypeface(android.graphics.Typeface.MONOSPACE);
            tvJson.setTextSize(13f);
            tvJson.setTextColor(Color.parseColor("#333333"));
            
            scrollView.addView(tvJson);

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Konfigurasi API / Klien")
                    .setView(scrollView)
                    .setPositiveButton("Tutup", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onLogout(View view) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Konfirmasi Logout")
                .setMessage("Apakah Anda yakin ingin logout? Anda harus memasukkan kode verifikasi lagi dari awal.")
                .setPositiveButton("Ya, Keluar", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        DatabaseHelper db = new DatabaseHelper(SettingsActivity.this);
                        db.clearAllSettings();
                        finish();

                        Intent intent = new Intent(SettingsActivity.this, WizardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
