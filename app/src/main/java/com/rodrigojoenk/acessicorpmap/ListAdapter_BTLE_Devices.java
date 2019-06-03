package com.rodrigojoenk.acessicorpmap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Array adapter que monta a lista de devices durante a execução
 */
public class ListAdapter_BTLE_Devices extends ArrayAdapter<BTLE_Device> {

    private Activity activity;
    private int layoutResourceID;
    private ArrayList<BTLE_Device> devices;
    private int position;
    private View convertView;
    private ViewGroup parent;

    ListAdapter_BTLE_Devices(Activity activity, int resource, ArrayList<BTLE_Device> objects) {
        super(activity.getApplicationContext(), resource, objects);

        this.activity = activity;
        layoutResourceID = resource;
        devices = objects;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) activity.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(layoutResourceID, parent, false);
        }

        BTLE_Device device = devices.get(position);
        String name = device.getName();
        String address = device.getAddress();
        int rssi = device.getRSSI();

        TextView tv;

        tv = convertView.findViewById(R.id.tv_name);
        if (name != null && name.length() > 0) {
            tv.setText(device.getName());
        }
        else {
            tv.setText("No Name");
        }

        tv = convertView.findViewById(R.id.tv_rssi);
        tv.setText(String.format("RSSI: %s", Integer.toString(rssi)));

        tv = convertView.findViewById(R.id.tv_macaddr);
        if (address != null && address.length() > 0) {
            tv.setText(device.getAddress());
        }
        else {
            tv.setText("No Address");
        }

        return convertView;
    }
}