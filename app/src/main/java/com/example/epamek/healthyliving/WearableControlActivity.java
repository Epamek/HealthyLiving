package com.example.epamek.healthyliving;

import android.app.Activity;

/**
 * Created by Epamek on 11/29/2017.
 */

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


//Provides a UI through with to connect and dispaly relevant GATT services and attributes.
    //Interacts with GATTCommService to interface with Bluetooth API
public class WearableControlActivity extends Activity {
    private final static String TAG = WearableControlActivity.class.getSimpleName();

    public static final String DEVICE_NAME = "DEVICE_NAME";
    public static final String DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private GATTCommService mGATTCommService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    public final UUID HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    public String heartRate;
    public int alarmTrigger;

    TextView textView;
    private static final String DB_URL ="jdbc:mysql://192.168.1.242:3306/heartrate";
    private static final String USER = "xyz";
    private static final String PASS = "pass";

    android.os.Handler customHandler;

    // Manages service connection
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mGATTCommService = ((GATTCommService.LocalBinder) service).getService();
            if (!mGATTCommService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Will attempt to automatically connect upon startup
            mGATTCommService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mGATTCommService = null;
        }
    };

    //Handles connection to GATT server, discovery of GATT data, and disconnection
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (GATTCommService.GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (GATTCommService.GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (GATTCommService.GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mGATTCommService.getSupportedGattServices());
            } else if (GATTCommService.DATA_AVAILABLE.equals(action)) {
                heartRate = intent.getStringExtra(GATTCommService.EXTRA);
                displayData(heartRate);
                if (Integer.parseInt(heartRate) > 95) {
                    alarmTrigger = 1;
                    SoundAlarm();
                }
            }
        }
    };

    //Checks for supported GATT services
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mGATTCommService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mGATTCommService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mGATTCommService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_page);

        final Intent intent = getIntent();
        String mDeviceName = intent.getStringExtra(DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(DEVICE_ADDRESS);

        // Sets up UI
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, GATTCommService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        textView = (TextView) findViewById(R.id.connect_Results);

        heartRate = "0";
        alarmTrigger = 0;

        customHandler = new android.os.Handler();
        customHandler.postDelayed(updateTimerThread, 30000);

        Button btn = findViewById(R.id.push);
        btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Send objSend = new Send();
                objSend.execute("");
            }
        });


        }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mGATTCommService != null) {
            final boolean result = mGATTCommService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mGATTCommService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mGATTCommService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mGATTCommService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    //Iterates through all available GATT services and characteristics, populating a data structure
    // (currently even if nature of service is unknown)
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through all services
        String LIST_NAME = "NAME";
        String LIST_UUID = "UUID";
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, WearableGATTs.nameLookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<>();

            // Loops through all characteristcs
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, WearableGATTs.nameLookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
                mGattCharacteristics.add(charas);
                gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GATTCommService.GATT_CONNECTED);
        intentFilter.addAction(GATTCommService.GATT_DISCONNECTED);
        intentFilter.addAction(GATTCommService.GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(GATTCommService.DATA_AVAILABLE);
        return intentFilter;
    }

    private class Send extends AsyncTask<String, String, String>
    {
        String msg = "";

        @Override
        protected void onPreExecute() {
            textView.setText("Please Wait Inserting Data");
        }

        @Override
        protected String doInBackground(String... strings)
        {
            try
            {
                msg = "Attempting to connect.";
                Class.forName("com.mysql.jdbc.Driver");
                msg = "Beginning connection";
                Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println(meta.getDatabaseProductName());
                System.out.println(meta.getDatabaseProductVersion());
                msg = "Establishing connection.";
                if (conn == null)
                {
                    msg = "Connection went wrong";
                }
                else
                {
                    msg = "Connection made.";
                    String query = "INSERT INTO heartrate (Time, Rate) VALUES(NOW(), '"+heartRate+"')";
                    Statement stmt = conn.createStatement();
                    msg = "Crafted query; attempting insertion";
                    stmt.executeUpdate(query);
                    msg = "Inserting Successful.";
                }
                conn.close();
            }
            catch (Exception e)
            {

                e.printStackTrace();
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String msg)
        {
            textView.setText(msg);
        }
    }

    private Runnable updateTimerThread = new Runnable()
    {
        public void run()
        {
            if (alarmTrigger == 1) {
                alarmTrigger = 0;
            }
            Send objSend = new Send();
            objSend.execute("");
            customHandler.postDelayed(this, 30000);
        }
    };

    private void SoundAlarm() {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        r.play();
    }

}
