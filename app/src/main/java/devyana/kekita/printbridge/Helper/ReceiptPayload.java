package devyana.kekita.printbridge.Helper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Kontrak JSON struk "bill". Spesifikasi lengkap: docs/receipt-json.md
 *
 * Menerima dua bentuk payload:
 *  - {"data": {...}, "items": [...]}  (dari API / preview)
 *  - objek datar {"invoice": ..., "items": [...]}
 *
 * Field opsional yang tidak ada diberi nilai default (string kosong / 0).
 * Field wajib dicek lewat {@link #validate(boolean)}.
 */
public class ReceiptPayload {

    public static class Item {
        public String name = "";
        public String variant = "";
        public String note = "";
        public int qty;
        public int subtotal;
    }

    // --- wajib ---
    public String invoice = "";
    public int totalGrand;                 // total_harus_dibayar
    public final List<Item> items = new ArrayList<>();

    // --- opsional (default string kosong / 0) ---
    public String date = "";               // tanggal_transaksi
    public String time = "";               // jam_transaksi
    public String table = "";              // meja
    public String cashier = "";            // nama_lengkap
    public String payment = "";            // pembayaran (kosong = belum dibayar)
    public int totalOrder;                 // total_pesanan (default: jumlah subtotal item)
    public int totalDiscount;              // total_diskon: diskon dari persentase produk
    public int totalDeduction;             // total_diskon_potongan: potongan nominal dari input
    public int totalVoucher;               // total_potongan: voucher
    public int totalService;
    public int totalTax;                   // total_ppn
    public int total;                      // total (default: totalGrand)
    public int rounding;                   // nilai_pembulatan
    public int totalPaid;                  // total_bayar / bayar
    public int totalExchange;              // total_kembali
    public String headerOverride = "";     // setting_header
    public String footerOverride = "";     // setting_footer

    private final JSONObject root;
    private final JSONObject data;
    private final JSONArray rawItems;

    private ReceiptPayload(JSONObject root, JSONObject data, JSONArray rawItems) {
        this.root = root;
        this.data = data;
        this.rawItems = rawItems;
    }

    public static ReceiptPayload parse(String payloadJson) throws JSONException {
        JSONObject root = new JSONObject(payloadJson);
        JSONObject data;
        JSONArray rawItems;
        if (root.has("data") && root.has("items")) {
            data = root.getJSONObject("data");
            rawItems = root.getJSONArray("items");
        } else {
            data = root;
            rawItems = root.optJSONArray("items");
            if (rawItems == null) rawItems = new JSONArray();
        }

        ReceiptPayload p = new ReceiptPayload(root, data, rawItems);

        p.invoice = str(data, "invoice");
        p.date = str(data, "tanggal_transaksi");
        p.time = str(data, "jam_transaksi");
        p.table = str(data, "meja");
        p.cashier = str(data, "nama_lengkap");

        String payment = str(data, "pembayaran");
        if (payment.trim().isEmpty()) {
            payment = str(data, "tipe_transaksi"); // fallback data dummy lama
        }
        p.payment = payment;

        int itemsSum = 0;
        for (int i = 0; i < rawItems.length(); i++) {
            JSONObject it = rawItems.optJSONObject(i);
            if (it == null) continue;
            Item item = new Item();
            item.name = str(it, "nama_produk");
            item.variant = str(it, "nama_varian");
            item.note = str(it, "catatan_item");
            item.qty = num(it, "jumlah_produk", 0);
            item.subtotal = num(it, "subtotal", 0);
            itemsSum += item.subtotal;
            p.items.add(item);
        }

        p.totalGrand = num(data, "total_harus_dibayar", 0);
        p.totalOrder = data.has("total_pesanan") ? num(data, "total_pesanan", 0) : itemsSum;
        p.totalDiscount = num(data, "total_diskon", 0);
        p.totalDeduction = num(data, "total_diskon_potongan", 0);
        p.totalVoucher = num(data, "total_potongan", 0);
        p.totalService = num(data, "total_service", 0);
        p.totalTax = num(data, "total_ppn", 0);
        p.total = data.has("total") ? num(data, "total", 0) : p.totalGrand;
        p.rounding = num(data, "nilai_pembulatan", 0);
        p.totalPaid = data.has("total_bayar") ? num(data, "total_bayar", 0) : num(data, "bayar", 0);
        p.totalExchange = num(data, "total_kembali", 0);

        String header = str(root, "setting_header");
        p.headerOverride = header.isEmpty() ? str(data, "setting_header") : header;
        String footer = str(root, "setting_footer");
        p.footerOverride = footer.isEmpty() ? str(data, "setting_footer") : footer;

        return p;
    }

    /**
     * Mengembalikan daftar field wajib yang hilang (kosong bila valid).
     *
     * @param requireQueueId true untuk payload dari antrean API: baris harus punya "id"
     *                       supaya bisa ditandai selesai (tanpa id struk akan tercetak berulang).
     */
    public List<String> validate(boolean requireQueueId) {
        List<String> missing = new ArrayList<>();
        if (requireQueueId && root.optInt("id", -1) == -1) missing.add("id");
        if (invoice.isEmpty()) missing.add("invoice");
        if (!data.has("total_harus_dibayar") || data.isNull("total_harus_dibayar")) missing.add("total_harus_dibayar");
        if (rawItems.length() == 0) {
            missing.add("items");
        } else {
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject it = rawItems.optJSONObject(i);
                if (it == null) {
                    missing.add("items[" + i + "]");
                    continue;
                }
                for (String key : new String[]{"nama_produk", "jumlah_produk", "subtotal"}) {
                    if (!it.has(key) || it.isNull(key)) missing.add("items[" + i + "]." + key);
                }
            }
        }
        return missing;
    }

    /** String dari JSON; kosong bila tidak ada, null, atau literal "null". */
    private static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equalsIgnoreCase(v) ? "" : v;
    }

    /** Angka dari JSON (number atau string seperti "143.000" / "-500"). */
    private static int num(JSONObject o, String key, int def) {
        if (o == null || !o.has(key) || o.isNull(key)) return def;
        Object v = o.opt(key);
        if (v instanceof Number) return (int) Math.round(((Number) v).doubleValue());
        return parseIntSafe(String.valueOf(v));
    }

    public static int parseIntSafe(String val) {
        try {
            boolean isNegative = val.trim().startsWith("-");
            String digitsOnly = val.replaceAll("[^0-9]", "");
            if (digitsOnly.isEmpty()) return 0;
            int value = Integer.parseInt(digitsOnly);
            return isNegative ? -value : value;
        } catch (Exception e) {
            return 0;
        }
    }
}
