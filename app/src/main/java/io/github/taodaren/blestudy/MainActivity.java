package io.github.taodaren.blestudy;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RequestExecutor;
import com.yanzhenjie.permission.SettingService;

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_BLE = "TAG_BLE";
    private static final int REQUEST_ENABLE_BLE = 0x01;// EJ_38192
    public static final ParcelUuid UUID_GATT_SERVICE
            = ParcelUuid.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initConfigBLE();
        setBLE();
    }

    /** BLE 初始化及全局配置 */
    private void initConfigBLE() {
        // 初始化
        BleManager.getInstance().init(getApplication());
        // 全局配置
        BleManager.getInstance()
                // 配置日志 → 默认打开库中的运行日志
                .enableLog(true)
                // 配置重连 → 设置连接时重连次数和重连间隔（毫秒），默认为 0 次不重连
                .setReConnectCount(1, 5000)
                // 配置分包发送 → 设置分包发送的时候，每一包的数据长度，默认 20 个字节
                .setSplitWriteNum(20)
                // 配置连接超时 → 设置连接超时时间（毫秒），默认 10 秒
                .setConnectOverTime(10000)
                // 配置操作超时 → 设置 readRssi、setMtu、write、read、notify、indicate 的超时时间（毫秒），默认 5 秒
                .setOperateTimeout(5000);
    }

    /** 判断 BLE 是否支持、打开 */
    private void setBLE() {
        // 判断当前设备是否支持 BLE
        if (BleManager.getInstance().isSupportBle()) {
            // 如果支持 BLE，判断蓝牙是否已经打开
            if (BleManager.getInstance().isBlueEnable()) {
                Log.i(TAG_BLE, "蓝牙已打开!");
                requestPermissions();
            } else {
                // 蓝牙未打开，通过系统弹出框的形式引导用户开启 BLE
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BLE);
            }
        } else {
            Toast.makeText(this, "对不起，您的设备不支持蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    /** 向用户解释为什么需要申请该权限 */
    private final Rationale mDefRationale = new Rationale() {
        @Override
        public void showRationale(Context context, List<String> permissions, final RequestExecutor executor) {
            List<String> permissionNames = Permission.transformText(context, permissions);
            String message = context.getString(R.string.message_permission_rationale,
                    TextUtils.join("\n", permissionNames));
            showDialogRationale(context, executor, message);
        }
    };

    /** 展示解释对话框 */
    private void showDialogRationale(Context context, final RequestExecutor executor, String message) {
        new AlertDialog.Builder(context)
                .setCancelable(false)
                .setTitle(R.string.dialog_tips)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_open, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executor.execute();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executor.cancel();
                    }
                })
                .show();
    }

    /** 6.0 及以上机型动态获取位置权限 */
    private void requestPermissions() {
        // 如果 API level 大于等于 23(Android 6.0) 时
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AndPermission.with(this)
                    .permission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    .rationale(mDefRationale)
                    // 被拒绝
                    .onDenied(new Action() {
                        @Override
                        public void onAction(List<String> permissions) {
                            // 如果一直否认权限
                            if (AndPermission.hasAlwaysDeniedPermission(MainActivity.this, permissions)) {
                                // 提示用户设置
                                showSetting(permissions);
                            }
                        }
                    })
                    // 授予
                    .onGranted(new Action() {
                        @Override
                        public void onAction(List<String> permissions) {
                            // 开始扫描
                            setScanRule();
                            scanBleDev();
                        }
                    })
                    .start();
        }
    }

    /** 配置扫描规则 */
    private void setScanRule() {
        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
//                // 只扫描指定的服务的设备，可选
//                .setServiceUuids(serviceUuids)
//                // 只扫描指定广播名的设备，可选
//                .setDeviceName(true, names)
//                // 只扫描指定 MAC 的设备，可选
//                .setDeviceMac(mac)
                // 连接时的 autoConnect 参数，可选，默认 false
                .setAutoConnect(false)
                // 扫描超时时间，可选，默认10秒；小于等于0表示不限制扫描时间
                .setScanTimeOut(0)
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);
    }

    /** 扫描设备 */
    private void scanBleDev() {
        BleManager.getInstance().scan(new BleScanCallback() {
            /**
             * 开始扫描（主线程）
             * 它会回到主线程，相当于 onScanning 设备之和
             *
             * @param scanResultList 本次扫描时段内所有被扫描且过滤后的设备集合
             */
            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                Log.i(TAG_BLE, "开始扫描");
                Log.i(TAG_BLE, "onScanFinished\n"
                        + "设备数--->" + +scanResultList.size());

                for (int i = 0; i < scanResultList.size(); i++) {
                    Log.i(TAG_BLE, "onScanFinished: " + scanResultList.get(i));
                }
            }

            /**
             * 扫描到一个符合扫描规则的 BLE 设备（主线程）
             *
             * @param success 本次扫描动作是否开启成功
             *                由于蓝牙没有打开，上一次扫描没有结束等原因，会造成扫描开启失败
             */
            @Override
            public void onScanStarted(boolean success) {
                Log.i(TAG_BLE, "onScanStarted: " + success);
            }

            /**
             * 扫描结束，列出所有扫描到的符合扫描规则的 BLE 设备（主线程）
             * 扫描过程中的所有过滤后的结果回调
             *
             * @param bleDevice 经过扫描过滤规则过滤后的设备，同一个设备只会出现一次
             */
            @Override
            public void onScanning(BleDevice bleDevice) {
                String bleDevName = bleDevice.getName();
                String mac = bleDevice.getMac();
                byte[] scanRecord = bleDevice.getScanRecord();
                int rssi = bleDevice.getRssi();
                Log.e(TAG_BLE, "onScanning"
                        + "\nNAME: " + bleDevName
                        + "\nMAC: " + mac
                        + "\nRECORD: " + scanRecord.length
                        + "\nRSSI: " + rssi
                );

                // 拿到设备对象之后，可以进行连接操作
                connBLE(bleDevice);
            }

            /**
             * 扫描过程中所有被扫描到的结果回调
             * 由于扫描及过滤的过程是在工作线程中的，此方法也处于工作线程中
             *
             * @param bleDevice 同一个设备会在不同的时间，携带自身不同的状态（比如信号强度等），出现在这个回调方法中，出现次数取决于周围的设备量及外围设备的广播间隔
             */
            @Override
            public void onLeScan(BleDevice bleDevice) {
                Log.d(TAG_BLE, "onLeScan--->"
                        + bleDevice.getName() + "丨"
                        + bleDevice.getMac()
                );
            }
        });
    }

    /** BLE 连接、断连、监控连接状态 */
    private void connBLE(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            /** 开始进行连接 */
            @Override
            public void onStartConnect() {
                Log.i(TAG_BLE, "DEV_MAC: " + bleDevice.getMac() + " 开始进行连接！");
            }

            /** 连接不成功 */
            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                Log.i(TAG_BLE, "DEV_MAC: " + bleDevice.getMac() + " 连接不成功！异常为：" + exception.getDescription());
            }

            /** 连接成功并发现服务 */
            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                Log.i(TAG_BLE, "DEV_MAC: " + bleDevice.getMac() + " 连接成功！");

                List<BluetoothGattService> serviceList = gatt.getServices();
                for (BluetoothGattService service : serviceList) {
                    UUID uuid_service = service.getUuid();
                    Log.i(TAG_BLE, "DEV_MAC: " + bleDevice.getMac() + " UUID_服务: " + uuid_service);

                    List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristicList) {
                        UUID uuid_chara = characteristic.getUuid();
                        Log.i(TAG_BLE, "DEV_MAC: " + bleDevice.getMac() + " UUID_特征: " + uuid_chara);
                    }
                }
            }

            /** 连接断开，特指连接后再断开的情况 */
            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                // 在这里可以监控设备的连接状态，一旦连接断开，可以根据自身情况考虑对BleDevice对象进行重连操作。
                // 需要注意的是，断开和重连之间最好间隔一段时间，否则可能会出现长时间连接不上的情况。
                // 此外，如果通过调用disconnect(BleDevice bleDevice)方法，主动断开蓝牙连接的结果也会在这个方法中回调，此时isActiveDisConnected将会是true

            }
        });
    }

    /** 提示用户手动设置权限 */
    private void showSetting(List<String> permissions) {
        List<String> permissionNames = Permission.transformText(this, permissions);
        String message = this.getString(R.string.message_permission_always_failed,
                TextUtils.join("\n", permissionNames));
        final SettingService settingService = AndPermission.permissionSetting(this);
        showDialogSet(message, settingService);
    }

    /** 展示设置对话框 */
    private void showDialogSet(String message, final SettingService settingService) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.dialog_tips)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_set, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settingService.execute();
                    }
                })
                .setNegativeButton(R.string.dialog_open_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settingService.cancel();
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLE) {
            // 判断蓝牙是否已经打开
            if (BleManager.getInstance().isBlueEnable()) {
                Toast.makeText(this, "蓝牙打开成功", Toast.LENGTH_SHORT).show();
                requestPermissions();
            } else {
                // 蓝牙未打开，通过系统弹出框的形式引导用户开启 BLE
                Toast.makeText(this, "无法使用蓝牙功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
