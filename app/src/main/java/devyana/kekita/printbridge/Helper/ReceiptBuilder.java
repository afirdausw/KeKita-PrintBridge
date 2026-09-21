package devyana.kekita.printbridge.Helper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;

import devyana.kekita.printbridge.Printer.EscPosFormatter;

public class ReceiptBuilder {

    public static String buildReceiptString(String payloadJson, int paperWidth, DatabaseHelper dbHelper) {
        try {
            JSONObject data;
            JSONArray items;
            
            // Periksa format JSON: dari PrinterService atau dari TestPrintActivity/SettingsActivity
            JSONObject root = new JSONObject(payloadJson);
            if (root.has("data") && root.has("items")) {
                data = root.getJSONObject("data");
                items = root.getJSONArray("items");
            } else {
                data = root;
                items = data.optJSONArray("items");
                if (items == null) items = new JSONArray();
            }

            // Update header dan footer dinamis jika ada dari payload struk
            String dynamicHeader = root.optString("setting_header", data.optString("setting_header", ""));
            String dynamicFooter = root.optString("setting_footer", data.optString("setting_footer", ""));

            if (dynamicHeader != null && !dynamicHeader.trim().isEmpty() && !"null".equalsIgnoreCase(dynamicHeader)) {
                dbHelper.saveSetting("header_text", dynamicHeader);
            }
            if (dynamicFooter != null && !dynamicFooter.trim().isEmpty() && !"null".equalsIgnoreCase(dynamicFooter)) {
                dbHelper.saveSetting("footer_text", dynamicFooter);
            }

            String template = dbHelper.getSetting("template");
            boolean isTemplate2 = "template_2".equals(template);
            boolean isTemplate3 = "template_3".equals(template);
            
            EscPosFormatter f = new EscPosFormatter(paperWidth);
            StringBuilder sb = new StringBuilder();

            String lang = dbHelper.getSetting("language");
            boolean isIndo = "indonesia".equalsIgnoreCase(lang);

            String lblInvoice = isTemplate3 ? "Kode Struk : " : (isIndo ? (isTemplate2 ? "Kode struk : " : "Invoice    : ") : (isTemplate2 ? "Invoice : " : "Invoice  : "));
            String lblTime    = isTemplate3 ? "Tanggal : " : (isIndo ? (isTemplate2 ? "Tanggal : " : "Waktu      : ") : (isTemplate2 ? "Time : " : "Time     : "));
            String lblCashier = isTemplate3 ? "Kasir : " : (isIndo ? (isTemplate2 ? "Kasir : " : "Kasir      : ") : (isTemplate2 ? "Cashier : " : "Cashier  : "));
            String lblTable   = isTemplate3 ? "No Meja : " : (isIndo ? (isTemplate2 ? "No Meja : " : "Meja       : ") : (isTemplate2 ? "Table : " : "Table    : "));
            String lblPayment = isTemplate3 ? "Pembayaran: " : (isIndo ? (isTemplate2 ? "Pembayaran: " : "Pembayaran : ") : (isTemplate2 ? "Payment : " : "Payment  : "));

            String lblDiscount = isTemplate3 ? "Diskon" : (isIndo ? "Diskon" : "Discount");
            String lblVoucher  = isTemplate3 ? "Potongan Voucher" : (isIndo ? "Potongan Voucher" : "Voucher Discount");
            String lblService  = isTemplate3 ? "Service Charge (5%)" : (isIndo ? "Layanan (5%)" : "Service Charge (5%)");
            String lblTax      = isTemplate3 ? "PPN (10%)" : (isIndo ? "PPN (10%)" : "PPN (10%)");
            String lblTotal    = "Total";
            String lblRounding = isTemplate3 ? "Pembulatan" : (isIndo ? "Pembulatan" : "Rounding");
            String lblTotalPaid= isTemplate3 ? "Total" : (isIndo ? "TOTAL DIBAYAR" : "TOTAL PAID");
            String lblPaid     = isTemplate3 ? "Dibayar" : (isIndo ? "Dibayar" : "Paid");
            String lblExchange = isTemplate3 ? "kembali" : (isIndo ? "Kembali" : "Exchange");
            String lblUnpaid   = isTemplate3 ? "--- Tagihan Belum Dibayar ---" : (isIndo ? "--- Tagihan Belum Dibayar ---" : "--- Unpaid Bill ---");
            String lblpaidBt   = isTemplate3 ? "*** Lunas ***" : (isIndo ? "*** Lunas ***" : "*** Paid ***");
            String lblThankYou = isTemplate3 ? "TERIMA KASIH" : (isIndo ? "TERIMA KASIH" : "THANK YOU");

            int totalItemCount = 0;
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    totalItemCount += parseIntSafe(it.optString("jumlah_produk", "0"));
                }
            }
            String lblSubtotal = (isTemplate2 || isTemplate3) ? ("Subtotal x" + totalItemCount) : "Subtotal";

            // === HEADER ===
            if (isTemplate2 || isTemplate3) {
                sb.append(f.center(dbHelper.getSetting("header_text"))).append("\n");
                // Tidak ada garis separator
            } else {
                sb.append(f.center(dbHelper.getSetting("header_text")));
                sb.append(f.separator());
            }

            // Info transaksi
            String rawInvoice = "#" + data.optString("invoice", "");
            if (isTemplate2 || isTemplate3) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(rawInvoice.getBytes());
                    byte[] digest = md.digest();
                    StringBuilder sbHash = new StringBuilder();
                    for (byte b : digest) {
                        sbHash.append(String.format("%02x", b));
                    }
                    rawInvoice = sbHash.toString().toUpperCase();
                } catch (Exception e) {
                    rawInvoice = rawInvoice.toUpperCase();
                }
                if (rawInvoice.length() > 13) {
                    rawInvoice = rawInvoice.substring(0, 13);
                }
            }
            sb.append(f.left(lblInvoice + rawInvoice));
            
            String jam = data.optString("jam_transaksi", "");

            if (isTemplate3) {
                sb.append(f.left(lblTable + data.optString("meja", "")));
                sb.append(f.left(lblTime + data.optString("tanggal_transaksi", "") + " " + jam));
                sb.append(f.left(lblCashier + data.optString("nama_lengkap", "")));
            } else if (isTemplate2) {
                if (jam != null && jam.length() >= 5) jam = jam.substring(0, 5);
                sb.append(f.left(lblTable + data.optString("meja", "")));
                sb.append(f.left(lblTime + data.optString("tanggal_transaksi", "") + " " + jam));
                sb.append(f.left(lblCashier + data.optString("nama_lengkap", "")));
            } else {
                if (jam != null && jam.length() >= 5) jam = jam.substring(0, 5);
                sb.append(f.left(lblTime + data.optString("tanggal_transaksi", "") + " " + jam));
                sb.append(f.left(lblCashier + data.optString("nama_lengkap", "")));
                sb.append(f.left(lblTable + data.optString("meja", "")));
            }

            String pembayaran = data.optString("pembayaran", "");
            if (pembayaran == null || pembayaran.trim().isEmpty()) {
                pembayaran = data.optString("tipe_transaksi", ""); // fallback for dummy data
            }
            
            boolean isCardOrEdc = pembayaran.toLowerCase().contains("card") || pembayaran.toLowerCase().contains("edc") || pembayaran.toLowerCase().contains("qris") || pembayaran.toLowerCase().contains("kartu") || pembayaran.toLowerCase().contains("transfer");
            
            if (!isTemplate2 && !isTemplate3) {
                if (pembayaran != null && !pembayaran.trim().isEmpty()) {
                    sb.append(f.left(lblPayment + pembayaran));
                }
            }
            sb.append(f.separator());

            // ITEMS
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

                    if (isTemplate2 || isTemplate3) {
                        sb.append(f.formatItemT2(name, qty, subtotal)).append("\n");
                    } else {
                        sb.append(f.formatItem(name, qty, subtotal)).append("\n");
                    }

                    if (note != null && !"null".equals(note) && !note.isEmpty()) {
                        if (isTemplate2 || isTemplate3) {
                            sb.append(f.left("(" + note + ")"));
                        } else {
                            sb.append(f.subLine(note));
                        }
                    }
                }
            }

            // TOTALS
            int totalDiskon = parseIntSafe(data.optString("total_diskon", data.optString("total_diskon_potongan", "0")));
            int totalPotongan = parseIntSafe(data.optString("total_potongan", "0"));
            int totalService = parseIntSafe(data.optString("total_service", "0"));
            int totalPPN = parseIntSafe(data.optString("total_ppn", "0"));
            int total = parseIntSafe(data.optString("total", "0"));
            int rounding = parseIntSafe(data.optString("nilai_pembulatan", "0"));
            int totalGrand = parseIntSafe(data.optString("total_harus_dibayar", "0"));
            int totalPaid = parseIntSafe(data.optString("total_bayar", data.optString("bayar", "0")));
            int totalExcange = parseIntSafe(data.optString("total_kembali", "0"));

            if (isCardOrEdc && totalPaid == 0) {
                totalPaid = totalGrand; // Jika bayar pakai kartu nilainya 0, samakan dengan total yang harus dibayar
            }

            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblSubtotal, f.formatNumber(data.optString("total_pesanan", "0"))));
            if (totalDiskon > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblDiscount, f.formatNumber(String.valueOf(totalDiskon))));
            if (totalPotongan > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblVoucher, f.formatNumber(String.valueOf(totalPotongan))));
            if (totalService > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblService, f.formatNumber(String.valueOf(totalService))));
            if (totalPPN > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblTax, f.formatNumber(String.valueOf(totalPPN))));
            if (!isTemplate3 && total != totalGrand) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblTotal, f.formatNumber(String.valueOf(total))));
            if (rounding != 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblRounding, f.formatNumber(String.valueOf(rounding))));
            
            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblTotalPaid, f.formatNumber(String.valueOf(totalGrand))));

            if (isTemplate3) {
                sb.append(f.feed(1));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", (pembayaran != null && !pembayaran.isEmpty() ? pembayaran : "Tunai"), f.formatNumber(String.valueOf(totalPaid))));
                if (pembayaran != null || !pembayaran.trim().isEmpty()) {
                    sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblExchange, f.formatNumber(String.valueOf(totalExcange))));
                }
            } else if (isTemplate2 && isCardOrEdc) {
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", pembayaran, f.formatNumber(String.valueOf(totalPaid))));
                sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblExchange, f.formatNumber("0")));
            } else {
                if (totalPaid != 0) {
                    sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblPaid, f.formatNumber(String.valueOf(totalPaid))));
                }
                if (totalExcange > 0) {
                    sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblExchange, f.formatNumber(String.valueOf(totalExcange))));
                }
            }

            if (!isTemplate3) {
                sb.append(f.separator());
            }
            sb.append(f.feed(1));

            if (pembayaran == null || pembayaran.trim().isEmpty()) {
                sb.append(f.center(lblUnpaid + "\n"));
            } else {
                sb.append(f.center(lblpaidBt + "\n"));
            }

            sb.append(f.center(dbHelper.getSetting("footer_text")));

            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing JSON: " + e.getMessage();
        }
    }

    private static int parseIntSafe(String val) {
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
