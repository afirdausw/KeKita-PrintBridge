package devyana.kekita.printbridge.Printer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import devyana.kekita.printbridge.Helper.DatabaseHelper;
import devyana.kekita.printbridge.Helper.EscPosImageHelper;
import devyana.kekita.printbridge.Activity.MainActivity;
import devyana.kekita.printbridge.R;

public class PrinterService extends Service {
    private static final String TAG = "PrinterService";
    public static final String PREFS = "printbridge_prefs";
    public static final String KEY_PRINTER_ADDR = "printer_address";
    public static final String KEY_PRINTER_NAME = "printer_name";
    public static final String KEY_PAPER_WIDTH = "paper_width";
    public static final String ACTION_PRINT_BYTES = "devyana.kekita.printbridge.PRINT2";
    public static final String ACTION_PRINT = "devyana.kekita.printbridge.PRINT";
    private static final String API_URL_OLD = "http://192.168.1.100/kusuma/api/";
    private static String API_URL = "";
    private static final String CHANNEL_ID = "print_bridge_channel";
    private static final int NOTIF_ID = 1001;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket socket;
    private OutputStream out;
    private BluetoothDevice connectedDevice;

    private ScheduledExecutorService scheduler;

    private DatabaseHelper dbHelper;

    @Override
    public void onCreate() {
        super.onCreate();

        dbHelper = new DatabaseHelper(this);
        API_URL = dbHelper.getSetting("url");

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Starting Print Bridge..."));

        // scheduler untuk tugas periodik (misal cek koneksi / polling web)
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // jalankan check koneksi dan update notifikasi setiap 10 detik
        scheduler.scheduleAtFixedRate(this::periodicTask, 2, 10, TimeUnit.SECONDS);
    }

    private void periodicTask() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String mac = prefs.getString(KEY_PRINTER_ADDR, null);
            String name = prefs.getString(KEY_PRINTER_NAME, null);

            if (mac == null) {
                updateNotification("No printer selected");
                disconnectSocket();
                return;
            }

            if (socket == null || !socket.isConnected()) {
                updateNotification("Connecting to " + (name != null ? name : mac) + " ...");
                boolean ok = tryConnect(mac);
                if (ok) {
                    updateNotification("Connected to " + (name != null ? name : mac));
                } else {
                    updateNotification("Failed to connect to " + (name != null ? name : mac));
                }
            } else {
                updateNotification("Connected to " + (name != null ? name : mac));
                // ✅ di sini kita panggil polling API
                checkPrintQueue();
            }
        } catch (Exception e) {
            Log.e(TAG, "periodicTask error", e);
            updateNotification("Error in service");
        }
    }

    private void checkPrintQueue() {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL + "print_queue");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    InputStream in = conn.getInputStream();
                    Scanner s = new Scanner(in).useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";
                    s.close();
                    conn.disconnect();

                    try {
                        // coba parse sebagai JSONArray dulu
                        JSONArray arr = new JSONArray(response);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject row = arr.getJSONObject(i);
                            processRow(row);
                        }
                    } catch (JSONException e) {
                        try {
                            // kalau gagal, berarti JSONObject tunggal
                            JSONObject row = new JSONObject(response);
                            processRow(row);
                        } catch (JSONException ex) {
                            Log.e(TAG, "Invalid JSON response: " + response, ex);
                        }
                    }
                } else {
                    conn.disconnect();
                    Log.w(TAG, "checkPrintQueue HTTP code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "checkPrintQueue error", e);
            }
        }).start();
    }

    private void processRow(JSONObject row) {
        // 1. Kalau queue kosong
        if (row.has("status") && "empty".equals(row.optString("status"))) {
            Log.d(TAG, "Queue kosong, tidak ada yang dicetak");
            return;
        }

        // 2. Ambil ID
        int id = row.optInt("id", -1);

        // 3. Cek tipe
        String tipe = row.optString("tipe", "bill");

        boolean printed = false;
        if ("bill".equalsIgnoreCase(tipe)) {
            printed = handlePayloadJson(row.toString());
        } else if ("recap".equalsIgnoreCase(tipe)) {
            printed = handleRecapPayloadJson(row.toString());
        } else {
            Log.w(TAG, "Tipe tidak dikenal: " + tipe);
        }

        // 4. Update status
        if (printed && id != -1) {
            markAsDone(id);
            Log.d(TAG, "Print OK (" + tipe + "), id=" + id);
        } else if (!printed && id != -1) {
            Log.w(TAG, "Print failed (" + tipe + ") untuk id=" + id);
        } else {
            Log.w(TAG, "Print failed (" + tipe + ", no id)");
        }
    }

    private boolean handlePayloadJson(String payloadJson) {
        try {
            JSONObject data = new JSONObject(payloadJson);

            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String widthStr = prefs.getString(KEY_PAPER_WIDTH, "58");
            int paperWidth = widthStr.equals("80") ? 45 : 31;

            EscPosFormatter f = new EscPosFormatter(paperWidth);
            StringBuilder sb = new StringBuilder();

            // Load logo dari drawable = AMAN
            byte[] logoBytes = null;
            String client = dbHelper.getSetting("client");
            if (client != null && !client.isEmpty()) {
                Bitmap logo = null;
                if (client.equals("Kusuma Kitchen")) {
                    logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_print_kusuma);
                } else if (client.equals("Biji Kopi")) {
                    logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_print_bijikopi);
                }
                logo = EscPosImageHelper.resizeBitmap(logo, 220);
                logo = EscPosImageHelper.toMonochrome(logo);
                logoBytes = EscPosImageHelper.decodeBitmap(logo);
            }

            // === HEADER ===
            sb.append(f.center(dbHelper.getSetting("header_text")));
            sb.append(f.separator());

            // Info transaksi
            sb.append(f.left("Invoice  : #" + data.optString("invoice", "")));
            String jam = data.optString("jam_transaksi", "");
            if (jam != null && jam.length() >= 5) jam = jam.substring(0, 5);
            sb.append(f.left("Time     : " + data.optString("tanggal_transaksi", "") + " " + jam));
            sb.append(f.left("Cashier  : " + data.optString("nama_lengkap", "")));
            sb.append(f.left("Table    : " + data.optString("meja", "")));

            String pembayaran = data.optString("pembayaran", "");
            if (pembayaran != null && !pembayaran.trim().isEmpty()) {
                sb.append(f.left("Payment  : " + pembayaran));
            }
            sb.append(f.separator());

            // ITEMS
            JSONArray items = data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String name = it.optString("nama_produk", "");
                    String varian = it.optString("nama_varian", "");
                    String note = it.optString("catatan_item", "");
                    int qty = parseIntSafe(it.optString("jumlah_produk", "0"));
                    int subtotal = parseIntSafe(it.optString("subtotal", "0"));

                    if (varian != null && !"null".equals(varian) && !varian.isEmpty()) {
                        name += " - " + varian;
                    }

                    sb.append(f.formatItem(name, qty, subtotal)).append("\n");

                    if (note != null && !"null".equals(note) && !note.isEmpty()) {
                        sb.append(f.subLine(note));
                    }
                }
            }

            // TOTALS
            int totalDiskon = parseIntSafe(data.optString("total_diskon", "0"));
            int totalPotongan = parseIntSafe(data.optString("total_potongan", "0"));
            int totalService = parseIntSafe(data.optString("total_service", "0"));
            int totalPPN = parseIntSafe(data.optString("total_ppn", "0"));
            int total = parseIntSafe(data.optString("total", "0"));
            int rounding = parseIntSafe(data.optString("nilai_pembulatan", "0"));
            int totalGrand = parseIntSafe(data.optString("total_harus_dibayar", "0"));
            int totalPaid = parseIntSafe(data.optString("total_bayar", "0"));
            int totalExcange = parseIntSafe(data.optString("total_kembali", "0"));

            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Subtotal", f.formatNumber(data.optString("total_pesanan", "0"))));
            // -- diskon item
            if (totalDiskon > 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Discount", f.formatNumber(String.valueOf(totalDiskon))));
            }
            // -- potongan
            if (totalPotongan > 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Voucher Discount", f.formatNumber(String.valueOf(totalPotongan))));
            }
            // -- service
            if (totalService > 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Service Charge (5%)", f.formatNumber(String.valueOf(totalService))));
            }
            // -- ppn
            if (totalPPN > 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Tax (10%)", f.formatNumber(String.valueOf(totalPPN))));
            }
            // -- total dari sebelumnya
            if (total != totalGrand) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Total", f.formatNumber(String.valueOf(total))));
            }
            // -- pembulatan
            if (rounding != 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Rounding", f.formatNumber(String.valueOf(rounding))));
            }
            // -- total akhir
            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "TOTAL PAID", f.formatNumber(String.valueOf(totalGrand))));

            if (totalPaid != 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Paid", f.formatNumber(String.valueOf(totalPaid))));
            }
            if (totalExcange > 0) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Exchange", f.formatNumber(String.valueOf(totalExcange))));
            }

            sb.append(f.separator()).append(f.feed(1));

            if (pembayaran.trim().isEmpty()) {
                sb.append(f.center("--- Tagihan Belum Dibayar ---\n"));
            }

            sb.append(f.center("THANK YOU\n"));
            sb.append(f.center(dbHelper.getSetting("footer_text")));

            // TODO: kirim ke PrinterService -> Bluetooth
            return printRaw(logoBytes, sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "handlePayloadJson error", e);
            return false;
        }
    }

    private boolean handleRecapPayloadJson(String payloadJson) {
        try {
            JSONObject data = new JSONObject(payloadJson);

            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String widthStr = prefs.getString(KEY_PAPER_WIDTH, "58");
            int paperWidth = widthStr.equals("80") ? 45 : 31;

            EscPosFormatter f = new EscPosFormatter(paperWidth);
            StringBuilder sb = new StringBuilder();

            // Load logo dari drawable
            byte[] logoBytes = null;

            // === HEADER ===
            sb.append(f.center(dbHelper.getSetting("header_text")));
            sb.append(f.separator());

            sb.append(f.center("LAPORAN PENJUALAN\n"));
            JSONObject laporan = data.optJSONObject("laporan");
            if (laporan != null) {
                sb.append(f.left("Dari   : " + laporan.optString("waktu_awal", "-")));
                sb.append(f.left("Sampai : " + laporan.optString("waktu_akhir", "-")));
            }
            sb.append(f.separator());

            // === PENJUALAN ===
            JSONObject penjualan = data.optJSONObject("penjualan");
            if (penjualan != null) {
                sb.append(f.center("PENJUALAN"));

                JSONArray items = penjualan.optJSONArray("items");
                if (items != null) {
                    Map<String, List<JSONObject>> kategori = new LinkedHashMap<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);

                        String jenis = it.optString("jenis_produk", "").trim();

                        if (jenis.isEmpty()) {
                            jenis = "Lainnya";
                        }

                        kategori.putIfAbsent(jenis, new ArrayList<>());
                        kategori.get(jenis).add(it);
                    }

                    for (Map.Entry<String, List<JSONObject>> entry : kategori.entrySet()) {
                        String namaKategori = entry.getKey().toUpperCase();
                        List<JSONObject> listItem = entry.getValue();

                        sb.append(f.left("\n== " + namaKategori + " =="));

                        for (JSONObject it : listItem) {
                            sb.append(f.formatRecapItem(f, it));
                        }
                    }
                }

                sb.append(f.separator());
                sb.append(f.center(laporan.optString("tanggal", "-")));
                sb.append(f.center(laporan.optString("waktu_awal", "-") + " - " + laporan.optString("waktu_akhir", "-")));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Subtotal",
                        f.formatNumber(penjualan.optString("subtotal", "0"))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Diskon",
                        f.formatNumber(penjualan.optString("diskon", "0"))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Potongan",
                        f.formatNumber(penjualan.optString("potongan", "0"))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Service (5%)",
                        f.formatNumber(penjualan.optString("service", "0"))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "PPN (10%)",
                        f.formatNumber(penjualan.optString("ppn", "0"))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Pembulatan",
                        f.formatNumber(penjualan.optString("pembulatan", "0"))));
                sb.append(f.separator());
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "TOTAL",
                        f.formatNumber(penjualan.optString("total", "0"))));
            }

            // === PENERIMAAN AKTUAL ===
            JSONObject penerimaanAktual = data.optJSONObject("penerimaan_aktual");
            if (penerimaanAktual != null) {
                sb.append(f.separator());
                sb.append(f.center("PENERIMAAN AKTUAL"));

                int tunai = penerimaanAktual.optInt("tunai", 0);
                if (tunai > 0)
                    sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Tunai", f.formatNumber(String.valueOf(tunai))));

                int qris = penerimaanAktual.optInt("qris", 0);
                if (qris > 0)
                    sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "QRIS", f.formatNumber(String.valueOf(qris))));

                JSONObject edc = penerimaanAktual.optJSONObject("edc");
                if (edc != null) {
                    Iterator<String> keys = edc.keys();
                    while (keys.hasNext()) {
                        String bank = keys.next();
                        int val = edc.optInt(bank, 0);
                        sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "EDC " + bank, f.formatNumber(String.valueOf(val))));
                    }
                }

                JSONObject transfer = penerimaanAktual.optJSONObject("transfer");
                if (transfer != null) {
                    Iterator<String> keys = transfer.keys();
                    while (keys.hasNext()) {
                        String bank = keys.next();
                        int val = transfer.optInt(bank, 0);
                        sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Transfer " + bank, f.formatNumber(String.valueOf(val))));
                    }
                }

                sb.append(f.separator());
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "TOTAL AKTUAL",
                        f.formatNumber(penerimaanAktual.optString("total", "0"))));

                sb.append(f.separator());
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "BELUM DIBAYAR",
                        f.formatNumber(penerimaanAktual.optString("belum_bayar", "0"))));
            }

            // === PENERIMAAN PERGANTIAN
            JSONObject penerimaanPergantian = data.optJSONObject("penerimaan_pergantian");
            if (penerimaanPergantian != null) {
                int tunai2 = penerimaanPergantian.optInt("tunai", 0);
                int qris2 = penerimaanPergantian.optInt("qris", 0);

                JSONObject edc2 = penerimaanPergantian.optJSONObject("edc");
                JSONObject transfer2 = penerimaanPergantian.optJSONObject("transfer");

                int total2 = penerimaanPergantian.optInt("total", 0);

                // cek apakah semuanya kosong/0
                boolean kosong = (tunai2 == 0) && (qris2 == 0) &&
                        (edc2 == null || edc2.length() == 0) &&
                        (transfer2 == null || transfer2.length() == 0) &&
                        (total2 == 0);

                if (!kosong) {
                    sb.append(f.separator());
                    sb.append(f.center("PENERIMAAN PERGANTIAN SHIFT")).append("\n");

                    if (tunai2 > 0)
                        sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Tunai", f.formatNumber(String.valueOf(tunai2))));

                    if (qris2 > 0)
                        sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "QRIS", f.formatNumber(String.valueOf(qris2))));

                    if (edc2 != null) {
                        Iterator<String> keys = edc2.keys();
                        while (keys.hasNext()) {
                            String bank = keys.next();
                            int val = edc2.optInt(bank, 0);
                            if (val > 0) {
                                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "EDC " + bank, f.formatNumber(String.valueOf(val))));
                            }
                        }
                    }

                    if (transfer2 != null) {
                        Iterator<String> keys = transfer2.keys();
                        while (keys.hasNext()) {
                            String bank = keys.next();
                            int val = transfer2.optInt(bank, 0);
                            if (val > 0) {
                                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Transfer " + bank, f.formatNumber(String.valueOf(val))));
                            }
                        }
                    }

                    sb.append(f.separator());
                    sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "TOTAL DARI PERGANTIAN SHIFT",
                            f.formatNumber(penerimaanPergantian.optString("total", "0"))));
                }
            }

            // === PENERIMAAN SISTEM ===
            JSONObject penerimaanSistem = data.optJSONObject("penerimaan_sistem");
            if (penerimaanSistem != null) {
                sb.append(f.separator());
                sb.append(f.center("PENERIMAAN SISTEM"));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Penjualan",
                        f.formatNumber(penerimaanSistem.optString("penjualan", "0"))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", "Retur",
                        f.formatNumber(penerimaanSistem.optString("retur", "0"))));
            }

            sb.append(f.separator()).append(f.feed(1));
            sb.append(f.center("=== END OF REPORT ==="));

            // kirim ke printer
            return printRaw(logoBytes, sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "handleRecapPayloadJson error", e);
            return false;
        }
    }

    private boolean printRaw(byte[] logoBytes, String text) {
        try {
            // Pastikan koneksi: kalau out null, coba connect berdasarkan prefs
            if (out == null || socket == null || !socket.isConnected()) {
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                String mac = prefs.getString(KEY_PRINTER_ADDR, null);
                if (mac == null) {
                    updateNotification("No printer selected (cannot print)");
                    Log.w(TAG, "No printer MAC saved");
                    return false;
                }
                boolean ok = tryConnect(mac);
                if (!ok) {
                    updateNotification("Failed to connect to printer (cannot print)");
                    return false;
                }
            }

            // kalau ada logo, print dulu
            if (logoBytes != null && logoBytes.length > 0) {
                out.write(new byte[]{0x1B, 0x61, 0x01}); // ESC a 1 → center
                out.write(logoBytes);
                out.write(new byte[]{0x1B, 0x61, 0x00}); // ESC a 0 → back to left
            }

            // print text biasa
            // Convert text to bytes. Thermal printers may expect GBK for locale chars — fallback ke UTF-8
            byte[] data;
            try {
                data = text.getBytes("GBK");
            } catch (Exception e) {
                data = text.getBytes("UTF-8");
            }

            // feed + cut (opsional)
            out.write(data);
            out.write(new byte[]{0x0A, 0x0A, 0x0A, 0x0A}); // feed 4 lines
            out.write(new byte[]{0x1D, 0x56, 0x00});
            out.flush();

            Log.i("PREVIEW", text.toString());
            Log.i(TAG, "Printed successfully (length=" + data.length + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "printRaw error", e);
            disconnectSocket(); // attempt reconnect next time
            return false;
        }
    }

    private int parseIntSafe(String s) {
        try {
            boolean isNegative = s.trim().startsWith("-");

            String digitsOnly = s.replaceAll("[^0-9]", "");
            if (digitsOnly.isEmpty()) {
                return 0;
            }
            int value = Integer.parseInt(digitsOnly);

            return isNegative ? -value : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private void markAsDone(int id) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(API_URL + "print_queue_update");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                String payload = "{\"id\":" + id + ",\"status\":\"done\"}";
                conn.getOutputStream().write(payload.getBytes("UTF-8"));
                conn.getOutputStream().flush();

                int resp = conn.getResponseCode();
                Log.i(TAG, "Mark as done resp=" + resp);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "markAsDone error", e);
            }
        }).start();
    }

    private boolean tryConnect(String mac) {
        disconnectSocket(); // bersihkan dulu

        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null || !btAdapter.isEnabled()) {
                Log.w(TAG, "Bluetooth not available or disabled");
                return false;
            }

            Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
            for (BluetoothDevice d : paired) {
                if (d.getAddress().equalsIgnoreCase(mac)) {
                    connectedDevice = d;
                    // create socket & connect (blocking)
                    socket = d.createRfcommSocketToServiceRecord(SPP_UUID);
                    socket.connect();
                    out = socket.getOutputStream();
                    Log.i(TAG, "Connected to printer: " + d.getName());
                    return true;
                }
            }

            Log.w(TAG, "Printer MAC not found in paired devices");
            return false;
        } catch (IOException e) {
            Log.e(TAG, "tryConnect failed", e);
            disconnectSocket();
            return false;
        }
    }

    private void disconnectSocket() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        out = null;
        connectedDevice = null;
    }

    private void updateNotification(String text) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Print Bridge")
                .setContentText(text)
                .setSmallIcon(R.drawable.twotone_print)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSound(null)
                .setVibrate(null)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(1001, notification);
        }
    }

    private Notification buildNotification(String contentText) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Print Bridge")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.twotone_print)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null);

        // Optional: tambahkan intent ke MainActivity saat user tap notif
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        b.setContentIntent(android.app.PendingIntent.getActivity(
                this, 0, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT | getPendingIntentMutability()));

        return b.build();
    }

    private int getPendingIntentMutability() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return android.app.PendingIntent.FLAG_MUTABLE;
        } else {
            return 0;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "Print Bridge Service";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Menjalankan Print Bridge (Bluetooth)");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Print Bridge")
                .setContentText("Service running and waiting for print jobs")
                .setSmallIcon(R.drawable.twotone_print)
                .setOngoing(true)
                .build();

        startForeground(1001, notification);

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PRINT.equals(action)) {
                String text = intent.getStringExtra("text");
                byte[] logo = intent.getByteArrayExtra("logo");
                if (text != null) {
                    printRaw(logo, text);
                }
            }
        }

        // Service sudah running via onCreate/startForeground
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) scheduler.shutdownNow();
        disconnectSocket();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
