package com.example.epamek.healthyliving;

/**
 * Created by Epamek on 11/29/2017.
 */

import java.util.HashMap;

class WearableGATTs {
    private static HashMap<String, String> wearableAttributes = new HashMap();
    static String HR_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Services.
        wearableAttributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        wearableAttributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Characteristics.
        wearableAttributes.put(HR_MEASUREMENT, "Heart Rate Measurement");
        wearableAttributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    static String nameLookup(String uuid, String originalName) {
        String newName = wearableAttributes.get(uuid);
        return newName == null ? originalName : newName;
    }
}
