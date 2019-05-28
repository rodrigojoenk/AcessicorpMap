package com.rodrigojoenk.acessicorpmap;

import android.bluetooth.BluetoothDevice;
/**
 * Created by Kelvin on 4/18/16.
 * Wrapper para os objetos bluetooth
 */
public class BTLE_Device {

    private BluetoothDevice bluetoothDevice;
    private int rssi; // Received Signal Strength Indicator, ou seja, indicador de intensidade do sinal
    private String name; // Nome do dispositivo

    BTLE_Device(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    String getAddress() {
        return bluetoothDevice.getAddress();
    }

    public String getName() {
        return bluetoothDevice.getName();
    }

    void setRSSI(int rssi) {
        this.rssi = rssi;
    }

    void setName(String name) {
        this.name = name;
    }

    int getRSSI() {
        return rssi;
    }
}
