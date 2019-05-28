package com.rodrigojoenk.acessicorpmap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;

class Scanner_BTLE {
    private MainActivity ma;
    private BluetoothAdapter mBluetoothAdapter;

    private boolean mScanning;
    private Handler mHandler;
    private long scanPeriod;

    private int signalStrength;

    Scanner_BTLE(MainActivity mainActivity, long scanPeriod, int signalStrength) {
        ma = mainActivity;

        mHandler = new Handler();

        this.scanPeriod = scanPeriod;
        this.signalStrength = signalStrength;

        final BluetoothManager bluetoothManager =
                (BluetoothManager) ma.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    boolean isScanning() {
        return mScanning;
    }

    void start() {
        if (!Utils.checkBluetooth(mBluetoothAdapter)) { //Teste se bluetooth está ativado
            Utils.requestUserBluetooth(ma);
            ma.stopScan();
        }
        else {
            scanLeDevice(true);
        }
    }

    void stop() {
        scanLeDevice(false);
    }

    private void scanLeDevice(final boolean enable) {

        if(enable && !mScanning) {
            Utils.toast(ma.getApplicationContext(), "Iniciando a busca de dispositivos BLE ");
            mHandler.postDelayed(new Runnable() { //O handler vai causar o atraso na busca
                @Override
                public void run() {
                    Utils.toast(ma.getApplicationContext(), "Finalizando a busca");
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    ma.stopScan();

                }
            }, scanPeriod );

            //Aqui inicia de verdade o scan
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback); //Chama o método nativo de busca
        }

    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            final int new_rssi = rssi;
            if(rssi > signalStrength) { //Verifica se o sinal do dispositivo se qualifica para ser adicionado a lista
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ma.addDevice(device, new_rssi, device.getName());
                    }
                });
            }
        }
    };
}
