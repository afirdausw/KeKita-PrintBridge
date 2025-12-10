package devyana.kekita.printbridge.Printer;

import org.json.JSONObject;

public class EscPosFormatter {

    private int paperWidth; // max chars per line

    public EscPosFormatter(int paperWidth) {
        this.paperWidth = paperWidth;
    }

    public String center(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.length() >= paperWidth) {
                result.append(line).append("\n");
                continue;
            }
            int spaces = (paperWidth - line.length()) / 2;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < spaces; i++) sb.append(" ");
            sb.append(line);
            result.append(sb).append("\n");
        }
        return result.toString();
    }

    public String left(String text) {
        return text + "\n";
    }

    public String right(String text) {
        if (text.length() >= paperWidth) return text + "\n";
        int spaces = paperWidth - text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) sb.append(" ");
        sb.append(text).append("\n");
        return sb.toString();
    }

    public String separator() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paperWidth; i++) sb.append("-");
        sb.append("\n");
        return sb.toString();
    }

    public String subLine(String text) {
        return "   (" + text + ")\n";
    }

    public String feed(int n) {
        return new String(new char[n]).replace("\0", "\n");
    }

    public String formatItem(String name, int qty, int subtotal) {
        String qtyName = qty + "x " + name;
        if (qtyName.length() > paperWidth - 10) qtyName = qtyName.substring(0, paperWidth - 10);
        String priceStr = String.valueOf(subtotal);
        return String.format("%-" + (paperWidth - 10) + "s %10s", qtyName, formatNumber(priceStr));
    }

    public String formatRecapItem(EscPosFormatter f, JSONObject it) {
        String name = it.optString("produk_nama", "");
        String varian = it.optString("produk_varian", "");
        int qty = parseIntSafe(it.optString("jumlah", "0"));
        int subtotal = parseIntSafe(it.optString("subtotal", "0"));

        if (varian != null && !varian.trim().isEmpty() && !"null".equals(varian)) {
            name += " - " + varian;
        }

        return f.formatItem(name, qty, subtotal) + "\n";
    }

    public String formatNumber(String val) {
        try {
            boolean isNegative = val.contains("-");
            long num = Long.parseLong(val.replaceAll("[^0-9]", ""));
            String formatted = String.format("%,d", num).replace(',', '.');

            return isNegative ? "-" + formatted : formatted;
        } catch (Exception e) {
            return val;
        }
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

}
