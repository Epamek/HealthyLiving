package com.example.epamek.healthyliving;

/**
 * Created by Epamek on 11/29/2017.
 */

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

//BluetoothScanActivity scans and displays available Bluetooth devices nearby. Selection leads to WearableControlActivity.
public class BluetoothScanActivity extends ListActivity {
    private ScannedDeviceAdapter mScannedDeviceAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 6000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_scan);
        mHandler = new Handler();

        //Determines whether Bluetooth LE is supported.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        //Initializes a Bluetooth adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;
        mBluetoothAdapter = bluetoothManager.getAdapter();

        //Determines whether the Android device supports Bluetooth
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_spinner);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mScannedDeviceAdapter.clear();
                scanDevices(true);
                break;
            case R.id.menu_stop:
                scanDevices(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Ensures Bluetooth is turned on.  If not, it requests that the user enable it via a pop up dialogue
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mScannedDeviceAdapter = new ScannedDeviceAdapter();
        setListAdapter(mScannedDeviceAdapter);
        scanDevices(true);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // should the user choose not to turn Bluetooth on
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanDevices(false);
        mScannedDeviceAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mScannedDeviceAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, WearableControlActivity.class);
        intent.putExtra(WearableControlActivity.DEVICE_NAME, device.getName());
        intent.putExtra(WearableControlActivity.DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanDevices(final boolean enable) {
        if (enable) {
            // Stops scanning after a short period. Now limited by Android 7.0 + to effectively be 6 seconds
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Holds any devices found during scanning
    private class ScannedDeviceAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mBluetoothDevices;
        private LayoutInflater mlayoutInflater;

        ScannedDeviceAdapter() {
            super();
            mBluetoothDevices = new ArrayList<BluetoothDevice>();
            mlayoutInflater = BluetoothScanActivity.this.getLayoutInflater();
        }

        void addDevice(BluetoothDevice device) {
            if(!mBluetoothDevices.contains(device)) {
                mBluetoothDevices.add(device);
            }
        }

        BluetoothDevice getDevice(int position) {
            return mBluetoothDevices.get(position);
        }

        void clear() {
            mBluetoothDevices.clear();
        }

        @Override
        public int getCount() {
            return mBluetoothDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mBluetoothDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            TextViewObject textView;
            // Optimizes a listview
            if (view == null) {
                view = mlayoutInflater.inflate(R.layout.device_list, null);
                textView = new TextViewObject();
                textView.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                textView.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(textView);
            } else {
                textView = (TextViewObject) view.getTag();
            }

            BluetoothDevice device = mBluetoothDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                textView.deviceName.setText(deviceName);
            else
                textView.deviceName.setText(R.string.unknown_device);
            textView.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    // Scanning callback
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mScannedDeviceAdapter.addDevice(device);
                            mScannedDeviceAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    static class TextViewObject {
        TextView deviceName;
        TextView deviceAddress;
    }
}
