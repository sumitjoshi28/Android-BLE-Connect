package com.example.sumit.bleConnection;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;



public class MainActivity extends AppCompatActivity {

    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;
    Button startScanningButton;
    Button stopScanningButton;
    BluetoothDevice bluetoothDevice;
    Button batteryLevel;
    String deviceName;
    String deviceAddress;
    TextView peripheralTextView;
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    Boolean btScanning = false;
    ArrayList<String> devicesDiscovered = new ArrayList<>();
    BluetoothGatt bluetoothGatt;
    private BluetoothGattService batteryBaseService;

    // Stops scanning after 10 seconds.
    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 10000;

    public static final UUID BATTERY_BASE_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE); //will hide the title
        getSupportActionBar().hide(); // hide the title bar

        setContentView(R.layout.activity_main);

        peripheralTextView = findViewById(R.id.PeripheralTextView);
        peripheralTextView.setMovementMethod(new ScrollingMovementMethod());

        startScanningButton = findViewById(R.id.StartScanButton);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });

        stopScanningButton = findViewById(R.id.StopScanButton);
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopScanning();
            }
        });
        stopScanningButton.setVisibility(View.INVISIBLE);

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();

        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        // Make sure we have access to coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
            checkPairedDevice();
        }

        batteryLevel = findViewById(R.id.battery_level);
        batteryLevel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readBatteryLevel();
            }
        });

    }

    // Device Scan Callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType,result);
            bluetoothDevice = result.getDevice();
            if(bluetoothDevice.getAddress().startsWith("80:EA"))
            {
                Log.d("BLETest", "onScanResult device found");
                BluetoothDevice selectedDevice = result.getDevice();
                deviceName = selectedDevice.getName();
                deviceAddress = selectedDevice.getAddress();
                peripheralTextView.append( "Device Name: " + deviceName + "Device Address: "+deviceAddress+  "\n");
                devicesDiscovered.add(selectedDevice.getAddress());

                try
                {
                    Thread.sleep(5000);
                    connectToDeviceSelected();
                }
                catch(InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }

            }

            // Auto Scroll for Text View.
            final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0) {
                peripheralTextView.scrollTo(0, scrollAmount);
            }
        }
    };

    // Device Connect Call back
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            // This will get called when a device connects or disconnects.
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("BLETest", "onConnectionStateChange " + gatt.getDevice().getAddress());
            System.out.println(newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
                peripheralTextView.setText("Device Connected \n");
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                peripheralTextView.append("Device disconnected OS: "+status+" NS: "+newState+ "\n");
            }
            else {
                peripheralTextView.append("Device connected/disconnected \n");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // This will get called after the client initiates a BluetoothGatt.discoverServices() call.
            super.onServicesDiscovered(gatt, status);
            Log.d("BLETest", "onServicesDiscovered" + gatt.getDevice().getAddress());

            if (status == BluetoothGatt.GATT_SUCCESS) {
                batteryBaseService = bluetoothGatt.getService(MainActivity.BATTERY_BASE_SERVICE_UUID);
                displayGattServices(bluetoothGatt.getServices());
            }

        }

        @Override
        // Result of a characteristic write operation.
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("BLETest", "onCharacteristicWrite");
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d("BLETest", "onCharacteristicChanged");
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        // Result of a characteristic read operation.
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d("BLETest", "onCharacteristicRead");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if(BATTERY_LEVEL_UUID.equals(characteristic.getUuid())){
                    byte[]data = characteristic.getValue();
                    if((data!=null) &&  (data.length > 0)){
                        int batteryLevel = (int)data[0];
                        Log.e("BLETest_Live","Battery Level = "+batteryLevel);
                        peripheralTextView.append("Battery Level: "+batteryLevel+"%"+ "\n");
                    }
                }

            } else {
                Log.d("BLETest", "read status failed");
            }
        }
    };

    // All the read and write characteristics methods.

    private void checkPairedDevice () {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    }

    private void readBatteryLevel(){
        if(bluetoothGatt == null || batteryBaseService == null){
            Log.e("BLETest", String.format("mBluetoothGatt || batteryBaseService null "));
            peripheralTextView.append(String.format("No device is connected."));
        }
        else {
            boolean success = bluetoothGatt.readCharacteristic(batteryBaseService.getCharacteristic(BATTERY_LEVEL_UUID));
            Log.d("BLETest", String.format("Battery Level read: %s", success));
            peripheralTextView.append(String.format("Reading Battery Level: %s \n",success));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("Coarse Location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    // Scanning of all the BLE devices.
    public void startScanning() {
        System.out.println("Start scanning");
        btScanning = true;
        peripheralTextView.setText("");
        peripheralTextView.append("Started Scanning\n");
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(leScanCallback);
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanning();
            }
        }, SCAN_PERIOD);
    }

    // Stop scanning button.
    public void stopScanning() {
        System.out.println("stopping scanning");
        peripheralTextView.append("Stopped Scanning\n");
        btScanning = false;
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    // Connect to selected device button.
    public void connectToDeviceSelected() {
        peripheralTextView.append("Trying to Connect to Device: " +deviceName +":" +deviceAddress+ "\n");
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                Log.i("BLETest", "Gatt closed in connect.");
            }
        } catch (Exception ignored) {
            bluetoothGatt = null;
            Log.e("BLETest", "Closing issue handled in connect.");
        }
        bluetoothGatt = bluetoothDevice.connectGatt(this, false, btleGattCallback);
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            final String uuid = gattService.getUuid().toString();
            System.out.println("Service Discovered: " + uuid);
            new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {

                final String charUuid = gattCharacteristic.getUuid().toString();
                System.out.println("Characteristic Discovered for Service: " + charUuid);
            }
        }
    }
}