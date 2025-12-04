package devyana.kekita.printbridge.Helper;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.OutputStream;
import java.util.UUID;

public class BluetoothPrinterHelper {
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream out;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "BTPrinterHelper";

    public BluetoothPrinterHelper(BluetoothDevice device) {
        this.device = device;
    }

    public boolean connect() {
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            out = socket.getOutputStream();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "connect failed", e);
            close();
            return false;
        }
    }

    public boolean write(byte[] data) {
        try {
            if (out == null) return false;
            out.write(data);
            out.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "write failed", e);
            return false;
        }
    }

    public void close() {
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }
}
