package com.tablemi.flutter_bluetooth_basic;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import static androidx.core.app.ActivityCompat.startActivityForResult;
import static androidx.core.content.ContextCompat.getSystemService;

/**
 * FlutterBluetoothBasicPlugin
 */
public class FlutterBluetoothBasicPlugin implements FlutterPlugin, MethodCallHandler, RequestPermissionsResultListener, ActivityAware, PluginRegistry.ActivityResultListener {
    private static final String TAG = "BluetoothBasicPlugin";
    private final int id = 0;
    private ThreadPool threadPool;
    private static final int REQUEST_COARSE_LOCATION_PERMISSIONS = 1451;
    private static final int REQUEST_ENABLE_BLUETOOTH = 12;
    private static final String NAMESPACE = "flutter_bluetooth_basic";
    private Boolean isReceiverRegistered = false;
    private Boolean isBluetoothConnected = false;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String NAME = "BTPrinter";

    private Activity activity;
    private Context context;
    private MethodChannel channel;
    private EventChannel stateChannel;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;

    private MethodCall pendingCall;
    private Result pendingResult;

    private final BroadcastReceiver mScanReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                HashMap<String, Object> deviceMap = new HashMap<>();
                deviceMap.put("name", device.getName());
                deviceMap.put("type", device.getType());
                deviceMap.put("address", device.getAddress());

                if (device != null && device.getName() != null && device.getAddress() != null) {
                    invokeMethodUIThread("ScanResult", deviceMap);
                }
            }
        }
    };

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
            result.error("bluetooth_unavailable", "Bluetooth is unavailable", null);
            return;
        }

        final Map<String, Object> args = call.arguments();

        switch (call.method) {
            case "state":
                state(result);
                break;
            case "isAvailable":
                result.success(mBluetoothAdapter != null);
                break;
            case "isOn":
                result.success(mBluetoothAdapter.isEnabled());
                break;
            case "isConnected":
                result.success(threadPool != null);
                break;
            case "startScan": {
                pendingCall = call;
                pendingResult = result;
                // Check if current android version is Android 10 or higher
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                                    != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                activity,
                                new String[]{
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                },
                                REQUEST_COARSE_LOCATION_PERMISSIONS);

                    } else {
                        startScan(call, result);
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                                    != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(
                                activity,
                                new String[]{
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                },
                                REQUEST_COARSE_LOCATION_PERMISSIONS);

                    } else {
                        startScan(call, result);
                    }
                }

                break;
            }
            case "stopScan":
                stopScan();
                result.success(true);
                break;
            case "connect":
                connect(result, args);
                break;
            case "disconnect":
                result.success(disconnect());
                break;
            case "destroy":
                result.success(destroy());
                break;
            case "writeData":
                writeData(result, args);
                break;
            default:
                result.notImplemented();
                break;
        }

    }

    private void state(Result result) {
        try {
            result.success(mBluetoothAdapter.getState());
        } catch (SecurityException e) {
            result.error("invalid_argument", "Argument 'address' not found", null);
        }

    }

    private void startScan(MethodCall call, Result result) {
        Log.d(TAG, "start scan ");

        try {
            if (mBluetoothAdapter != null) {
                boolean isEnableBluetooth = mBluetoothAdapter.isEnabled();

                if (isEnableBluetooth) {
                    startScan();
                } else {
                    Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(activity, enableBT, REQUEST_ENABLE_BLUETOOTH, null);
                }
            }

            result.success(null);
        } catch (Exception e) {
            result.error("startScan", e.getMessage(), null);
        }
    }

    private void invokeMethodUIThread(final String name, final Map<String, Object> params) {
        final Map<String, Object> ret = new HashMap<>();

        if (name == "ScanResult") {
            ret.put("address", params.get("address"));
            ret.put("name", params.get("name"));
            ret.put("type", params.get("type"));
        }

        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        channel.invokeMethod(name, ret);
                    }
                });
    }

    private void startScan() {
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.i(TAG, "getBluetoothLeScanner() is null. Is the Adapter on?");
            throw new IllegalStateException("getBluetoothLeScanner() is null. Is the Adapter on?");
        }

        this.isReceiverRegistered = true;
        if (this.mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        try {
            mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, MY_UUID);
            mBluetoothAdapter.startDiscovery();
            context.registerReceiver(mScanReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            context.registerReceiver(mScanReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopScan() {
        if (isReceiverRegistered) {
            context.unregisterReceiver(this.mScanReceiver);
            this.isReceiverRegistered = false;
        }
        if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

    }

    private void connect(final Result result, Map<String, Object> args) {
        try {
            if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
            if (args.containsKey("address")) {
                String address = (String) args.get("address");
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                if (device == null) {
                    Log.e(TAG, "Printer Device Is Unknown");
                    result.error("connection_error", "Device not found", null);
                    return;
                }
                boolean isConnectSameDevice = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT).contains(device);
                if (!isConnectSameDevice && threadPool != null && result != null) {
                    disconnect();
                    Log.d(TAG, "Disconnect current printer device : " + address);
                }

                new DeviceConnFactoryManager.Build()
                        .setId(id)
                        // Set the connection method
                        .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                        // Set the connected Bluetooth mac address
                        .setMacAddress(address)
                        .build();
                // Open port
                threadPool = ThreadPool.getInstantiation();
                threadPool.addSerialTask(new Runnable() {
                    @Override
                    public void run() {
//                        try {
                        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
//                            if (isSubmittedResultStatusConnect != true && result != null) {
//                                result.success(true);
//                            }
//                            isSubmittedResultStatusConnect = true;

//                        } catch (Exception e) {
//                            Log.e(TAG, "Failure connect to device " + e.toString());
//                            if (isSubmittedResultStatusConnect != true && result != null) {
//                                result.error("connection_error", "Failure connect to device " + e.toString(), null);
//                            }
//                            isSubmittedResultStatusConnect = true;
//
//                        }
                    }
                });
//                TODO: check success connect or not
                result.success(true);
            } else {
                Log.e(TAG, "Argument 'address' not found");
                result.error("connection_error", "Argument 'address' not found", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failure connect to device");
            result.error("connection_error", "Failure connect to device " + e.toString(), null);
        }
    }

    /**
     * Reconnect to recycle the last connected object to avoid memory leaks
     */
    private boolean disconnect() {
        DeviceConnFactoryManager manager = DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id];
        if (manager != null && manager.mPort != null && manager.reader != null) {
            manager.reader.cancel();
            manager.mPort.closePort();
            manager.mPort = null;
            Log.d(TAG, "Successfully disconnect a printer device");
        }
        return true;

    }

    private boolean destroy() {
        DeviceConnFactoryManager.closeAllPort();
        if (threadPool != null) {
            threadPool.stopThreadPool();
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void writeData(Result result, Map<String, Object> args) {
        if (args.containsKey("bytes")) {
            final ArrayList<Integer> bytes = (ArrayList<Integer>) args.get("bytes");

            threadPool = ThreadPool.getInstantiation();
            threadPool.addSerialTask(new Runnable() {
                @Override
                public void run() {
                    Vector<Byte> vectorData = new Vector<>();
                    for (int i = 0; i < bytes.size(); ++i) {
                        Integer val = bytes.get(i);
                        vectorData.add(Byte.valueOf(Integer.toString(val > 127 ? val - 256 : val)));
                    }

                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(vectorData);

                }
            });
        } else {
            result.error("bytes_empty", "Bytes param is empty", null);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_COARSE_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Log.i(TAG, "RESULT ON Request Permission in Android Q");
                    if (grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                        startScan(pendingCall, pendingResult);
                    }
                } else {
                    startScan(pendingCall, pendingResult);
                }
            } else {
                pendingResult.error("no_permissions", "This app requires location permissions for scanning", null);
                pendingResult = null;
            }
            return true;
        }
        return false;

    }

    private final StreamHandler stateStreamHandler = new StreamHandler() {
        private EventSink sink;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                Log.d(TAG, "stateStreamHandler, current action: " + action);

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    threadPool = null;
                    sink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
                    isBluetoothConnected = false;
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    isBluetoothConnected = true;
                    sink.success(1);
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    isBluetoothConnected = false;
                    threadPool = null;
                    sink.success(0);
                } else {
                    isBluetoothConnected = false;
                }
            }
        };

        @Override
        public void onListen(Object o, EventSink eventSink) {
            sink = eventSink;
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            activity.registerReceiver(mReceiver, filter);
        }

        @Override
        public void onCancel(Object o) {
            sink = null;
            activity.unregisterReceiver(mReceiver);
        }
    };


    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            Log.d(TAG, "RESULT REQUEST ENABLE BLUETOOTH, 'RESULT_CODE': " + resultCode);
            if (mBluetoothAdapter.isEnabled()) {
                startScan();
            }
        }
        return false;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        context = binding.getActivity().getApplicationContext();
        activity = binding.getActivity();
        mBluetoothManager = (BluetoothManager) binding.getActivity().getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
        context = null;
        mBluetoothManager = null;
        mBluetoothAdapter = null;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), NAMESPACE + "/methods");
        channel.setMethodCallHandler(this);
        stateChannel = new EventChannel(binding.getBinaryMessenger(), NAMESPACE + "/state");
        stateChannel.setStreamHandler(stateStreamHandler);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        stateChannel.setStreamHandler(null);
    }
}