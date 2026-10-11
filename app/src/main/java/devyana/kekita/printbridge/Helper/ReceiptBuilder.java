package devyana.kekita.printbridge.Helper;

import java.security.MessageDigest;

import devyana.kekita.printbridge.Printer.EscPosFormatter;

public class ReceiptBuilder {

    public static String buildReceiptString(String payloadJson, int paperWidth, DatabaseHelper dbHelper) {
        try {
            // Format JSON (wajib/opsional + default): lihat ReceiptPayload & docs/receipt-json.md
            ReceiptPayload p = ReceiptPayload.parse(payloadJson);

            // Update header dan footer dinamis jika ada dari payload struk
            if (!p.headerOverride.trim().isEmpty()) {
                dbHelper.saveSetting("header_text", p.headerOverride);
            }
            if (!p.footerOverride.trim().isEmpty()) {
                dbHelper.saveSetting("footer_text", p.footerOverride);
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
            String lblDeduction = isTemplate3 ? "Potongan" : (isIndo ? "Potongan" : "Deduction");
            String lblVoucher  = isTemplate3 ? "Potongan Voucher" : (isIndo ? "Potongan Voucher" : "Voucher Discount");
            String lblService  = isTemplate3 ? "Service Charge (5%)" : (isIndo ? "Layanan (5%)" : "Service Charge (5%)");
            String lblTax      = isTemplate3 ? "PPN (10%)" : (isIndo ? "PPN (10%)" : "PPN (10%)");
            String lblTotal    = "Total";
            String lblRounding = isTemplate3 ? "Pembulatan" : (isIndo ? "Pembulatan" : "Rounding");
            String lblTotalPaid= isTemplate3 ? "Total" : (isIndo ? "TOTAL DIBAYAR" : "TOTAL PAID");
            String lblPaid     = isTemplate3 ? "Dibayar" : (isIndo ? "Dibayar" : "Paid");
            String lblExchange = isTemplate3 ? "kembali" : (isIndo ? "Kembali" : "Exchange");
            String lblUnpaid   = isTemplate3 ? "--- Tagihan Belum Dibayar ---" : (isIndo ? "--- Tagihan Belum Dibayar ---" : "--- Unpaid Bill ---");
            String lblpaidBt   = isTemplate3 ? "** LUNAS **" : (isIndo ? "*** Lunas ***" : "*** Paid ***");
            String lblThankYou = isTemplate3 ? "TERIMA KASIH" : (isIndo ? "TERIMA KASIH" : "THANK YOU");

            int totalItemCount = 0;
            for (ReceiptPayload.Item it : p.items) {
                totalItemCount += it.qty;
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
            String useHash = dbHelper.getSetting("use_hash_invoice");
            boolean addHash = (useHash == null) || "true".equals(useHash);
            String rawInvoice = p.invoice;
            // Default: tidak diacak. Berlaku untuk semua template.
            boolean randomize = "true".equals(dbHelper.getSetting("randomize_invoice"));
            if (randomize) {
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
            // Tanda # selalu ditambahkan setelah pengacakan, tidak ikut teracak
            if (addHash) rawInvoice = "#" + rawInvoice;
            sb.append(f.left(lblInvoice + rawInvoice));
            
            String jam = p.time;

            boolean hasTable = p.table != null && !p.table.trim().isEmpty();

            if (isTemplate3) {
                String mejaStr = p.table;
                try {
                    int m = Integer.parseInt(mejaStr);
                    mejaStr = String.valueOf(m);
                } catch(Exception ignored) {}
                
                String tglStr = p.date;
                if (tglStr.length() >= 10) {
                    try {
                        String y = tglStr.substring(0, 4);
                        String m = tglStr.substring(5, 7);
                        String d = tglStr.substring(8, 10);
                        tglStr = d + "-" + m + "-" + y;
                    } catch(Exception ignored) {}
                }
                
                if (hasTable) sb.append(f.left(lblTable + mejaStr));
                sb.append(f.left(lblTime + tglStr + " " + jam));
                sb.append(f.left(lblCashier + p.cashier));
            } else if (isTemplate2) {
                if (jam != null && jam.length() >= 5) jam = jam.substring(0, 5);
                if (hasTable) sb.append(f.left(lblTable + p.table));
                sb.append(f.left(lblTime + p.date + " " + jam));
                sb.append(f.left(lblCashier + p.cashier));
            } else {
                if (jam != null && jam.length() >= 5) jam = jam.substring(0, 5);
                sb.append(f.left(lblTime + p.date + " " + jam));
                sb.append(f.left(lblCashier + p.cashier));
                if (hasTable) sb.append(f.left(lblTable + p.table));
            }

            String pembayaran = p.payment;

            boolean isCardOrEdc = pembayaran.toLowerCase().contains("card") || pembayaran.toLowerCase().contains("edc") || pembayaran.toLowerCase().contains("qris") || pembayaran.toLowerCase().contains("kartu") || pembayaran.toLowerCase().contains("transfer");
            
            if (!isTemplate2 && !isTemplate3) {
                if (pembayaran != null && !pembayaran.trim().isEmpty()) {
                    sb.append(f.left(lblPayment + pembayaran));
                }
            }
            sb.append(f.separator());

            // ITEMS
            for (ReceiptPayload.Item it : p.items) {
                String name = it.name;
                if (!it.variant.isEmpty()) {
                    name += " - " + it.variant;
                }

                if (isTemplate2 || isTemplate3) {
                    sb.append(f.formatItemT2(name, it.qty, it.subtotal)).append("\n");
                } else {
                    sb.append(f.formatItem(name, it.qty, it.subtotal)).append("\n");
                }

                if (!it.note.isEmpty()) {
                    if (isTemplate2 || isTemplate3) {
                        sb.append(f.left("(" + it.note + ")"));
                    } else {
                        sb.append(f.subLine(it.note));
                    }
                }
            }

            // TOTALS
            int totalDiskon = p.totalDiscount;
            int totalDeduction = p.totalDeduction;
            int totalPotongan = p.totalVoucher;
            int totalService = p.totalService;
            int totalPPN = p.totalTax;
            int total = p.total;
            int rounding = p.rounding;
            int totalGrand = p.totalGrand;
            int totalPaid = p.totalPaid;
            int totalExcange = p.totalExchange;

            if (isCardOrEdc && totalPaid == 0) {
                totalPaid = totalGrand; // Jika bayar pakai kartu nilainya 0, samakan dengan total yang harus dibayar
            }

            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblSubtotal, f.formatNumber(String.valueOf(p.totalOrder))));
            if (totalDiskon > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblDiscount, f.formatNumber(String.valueOf(totalDiskon))));
            if (totalDeduction > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblDeduction, f.formatNumber(String.valueOf(totalDeduction))));
            if (totalPotongan > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblVoucher, f.formatNumber(String.valueOf(totalPotongan))));
            if (totalService > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblService, f.formatNumber(String.valueOf(totalService))));
            if (totalPPN > 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblTax, f.formatNumber(String.valueOf(totalPPN))));
            if (!isTemplate3 && total != totalGrand) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblTotal, f.formatNumber(String.valueOf(total))));
            if (rounding != 0) sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblRounding, f.formatNumber(String.valueOf(rounding))));
            
            sb.append(f.separator());
            sb.append(String.format("%-" + (paperWidth - 10) + "s %10s\n", lblTotalPaid, f.formatNumber(String.valueOf(totalGrand))));

            if (isTemplate3) {
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

            if (!isTemplate2 && !isTemplate3) {
                sb.append(f.separator());
            }
            sb.append(f.feed(1));

            if (pembayaran == null || pembayaran.trim().isEmpty()) {
                sb.append(f.center(lblUnpaid + "\n"));
            } else {
                sb.append(f.center(lblpaidBt + "\n"));
            }

            if (isTemplate3) {
                sb.append("\n");
            }

            sb.append(f.center(dbHelper.getSetting("footer_text")));

            if (isTemplate3) {
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing JSON: " + e.getMessage();
        }
    }
}
